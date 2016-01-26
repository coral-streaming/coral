/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.api.security

import java.util.UUID
import akka.actor.ActorSelection
import akka.util.Timeout
import io.coral.actors.CoralActor.Shunt
import io.coral.api.security.Authenticator.{GetUserTable, InvalidationComplete, Authenticate}
import io.coral.api.CoralConfig
import spray.routing.authentication.{BasicAuth, UserPass}
import spray.routing.directives.AuthMagnet
import scala.concurrent.{Future, ExecutionContext, Await}
import akka.pattern.ask
import org.json4s._
import org.json4s.JsonDSL._
import scala.concurrent.duration._
import akka.pattern.pipe

object CoralAuthenticator {
	def coralAuthenticator(cassandra: ActorSelection,
						   authenticatorActor: ActorSelection)(
							  implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
		def validateUser(userPass: Option[UserPass]): Future[Option[AuthInfo]] = {
			implicit val timeout = Timeout(10.seconds)
			authenticatorActor.ask(Authenticate(userPass, cassandra))
				.asInstanceOf[Future[Option[AuthInfo]]]
		}

		def authenticator(userPass: Option[UserPass]): Future[Option[AuthInfo]] =
			validateUser(userPass)

		BasicAuth(authenticator _, realm = "Coral")
	}
}

/**
 * The internal Coral Authenticator, storing users and
 * passwords in the authentication table in Cassandra.
 * @param config The CoralConfig object that contains the
 *               authentication settings.
 */
class CoralAuthenticator(config: CoralConfig)(implicit ec: ExecutionContext)
	extends Authenticator(config) {
	override implicit val formats = org.json4s.DefaultFormats

	// <uniqueUserName, AuthInfo> tuples
	var users: Map[String, AuthInfo] = _

	override def preStart() = {
		refreshUsers()
		refreshPermissions()
		super.preStart()
	}

	override def invalidate() = {
		val originalSender = sender()
		refreshUsers()
		refreshRuntimeTable()
		refreshPermissions()
		originalSender ! InvalidationComplete()
	}

	def refreshUsers() {
		val cassandra = context.system.actorSelection("/user/root/cassandra")
		// Will have to wait for the full user table here...
		users = Await.result(combineUsersAndPermissions(cassandra),
			timeout.duration).asInstanceOf[Map[String, AuthInfo]]
	}

	def refreshPermissions() {
		users = users.map(tuple => {
			val uniqueName = tuple._1
			val oldAuthInfo = tuple._2
			val oldUser = oldAuthInfo.user
			val permissions = getPermissionsFromDatabase(oldAuthInfo.id)
			val newAuthInfo = AuthInfo(oldUser, permissions)
			(uniqueName, newAuthInfo)
		})
	}

	override def receive = super.receive orElse {
		case GetUserTable() =>
			getUserTable()
		case _ =>
	}

	override def getAuthInfo(uniqueUserName: String): Option[AuthInfo] = {
		users.get(uniqueUserName)
	}

	/**
	 * Find a user by its ID. Since users are stored by their unique name,
	 * finding them by their ID needs to be implemented separately.
	 * @param userId The userId to find.
	 * @return Some(user) with the given ID, if it exists, None otherwise.
	 */
	override def getAuthInfo(userId: UUID): Option[AuthInfo] = {
		users.find(u => u._2.user.isLeft &&
			u._2.user.left.get.id == userId).map(_._2)
	}

	/**
	 * Get an object containing the authorization info for a certain user.
	 * This method is used to fetch the AuthInfo object from the HTTP
	 * Basic Auth user and password.
	 * @param up The user/password combination to get the authorization info for.
	 * @return An object containing the authorization info for the user.
	 */
	override def getAuthInfo(up: UserPass): Option[AuthInfo] = {
		val authInfo = getAuthInfo(up.user)

		authInfo match {
			case None => None
			case Some(ai) =>
				ai.user match {
					case Left(u) =>
						if (!u.passwordMatches(up.pass)) {
							None
						} else {
							authInfo
						}
				}
		}
	}

	/**
	 * Combine the user and permission table into a single map referenced by unique name.
	 * cassandra The cassandra driver to get data from
	 * A map containing <uniqueName, AuthInfo> tuples.
	 */
	def combineUsersAndPermissions(cassandra: ActorSelection): Future[Map[String, AuthInfo]] = {
		log.info("Fetching user and permission table...")

		// The map of <uniqueUserName, User> tuples
		val userMap: Future[Map[String, User]] = getUserTable(cassandra)
		// A list of all permissions for all users
		val authorizationList: Future[List[Permission]] = getAuthorizationTable(cassandra)

		val answer = Await.result(userMap.flatMap(um => authorizationList.map(al => {
			// Group the permission list by sublist per user
			val groupedById: Map[UUID, List[Permission]] = al.groupBy(_.user)

			// Convert the first entry <uniqueUserName, user>
			// and the second entry <uniqueUserName, permission>
			// to a combined element <uniqueUserName, AuthInfo(user, permission)>
			val result = um.map(elem => {
				val id: String = elem._1
				val user: User = elem._2
				val permissions = groupedById.getOrElse(user.id, List())
				(id -> AuthInfo(Left(user), permissions))
			})

			log.info("Finished fetching user and permission table.")
			result
		})), timeout.duration)

		Future.successful(answer)
	}

	def getUserTable(): Future[Map[String, User]] = {
		val originalSender = sender()
		val cassandra = context.actorSelection("/user/root/cassandra")
		getUserTable(cassandra) pipeTo originalSender
	}

	/**
	 * Fetches the user table from Cassandra and converts it to
	 * a map of unique user names to User objects.
	 * @param cassandra The Cassandra actor to fetch the data from.
	 * @return A Map containing <uniqueUserName, User> pairs.
	 */
	def getUserTable(cassandra: ActorSelection): Future[Map[String, User]] = {
		log.info("Fetching user table...")

		val query: JObject = ("query" -> s"""select * from
			   | ${config.coral.cassandra.keyspace}.${config.coral.cassandra.userTable};""".stripMargin)
		val future = cassandra.ask(Shunt(query)).asInstanceOf[Future[JObject]]
		convertUserTable(future)
	}

	/**
	 * Converts the user table retrieved from the Cassandra actor in JSON format
	 * to a map of <uniqueUserName, user> tuples.
	 * @param userTable The JSON table to convert.
	 * @return A map with users, accessible by unique user name.
	 */
	def convertUserTable(userTable: Future[JObject]): Future[Map[String, User]] = {
		userTable.map(u => {
			val users = u \ "data"

			users.children.map(user => {
				val id = UUID.fromString((user \ "id").extract[String])
				val fullName = (user \ "fullname").extract[String]
				val department = (user \ "department").extractOpt[String]
				val email = (user \ "email").extract[String]
				val uniqueUserName = (user \ "uniquename").extract[String]
				val mobilePhone = (user \ "mobilephone").extractOpt[String]
				val hashedPassword = (user \ "hashedpassword").extract[String]
				val createdOn = (user \ "createdon").extract[Long]
				val lastLogin = (user \ "lastlogin").extractOpt[Long]

				uniqueUserName -> User(id, fullName, department, email, uniqueUserName,
					mobilePhone, hashedPassword, createdOn, lastLogin)
			}).toMap
		})
	}
}