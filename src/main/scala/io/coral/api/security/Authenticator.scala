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
import akka.actor._
import akka.util.Timeout
import io.coral.actors.CoralActor.Shunt
import io.coral.api.security.Authenticator._
import io.coral.api.CoralConfig
import io.coral.utils.Utils
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import spray.http.Uri
import spray.routing.authentication.UserPass
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._
import akka.pattern.ask
import io.coral.api.Runtime
import akka.pattern.pipe

object Authenticator {
	case class CheckRuntime(runtimeName: String, authInfo: AuthInfo, cassandraActor: ActorSelection)
	case class Authenticate(userPass: Option[UserPass], cassandraActor: ActorSelection)
	case class Authorize(uri: Uri, userName: String)
	case class GetOwnerPermissionUUID(runtime: Runtime)
	case class CheckNameAndUser(name: String, owner: UUID)
	case class UserAndRuntimeExist(user: UUID, runtime: UUID)
	case class GetUserUUIDFromUniqueName(uniqueName: String)
	case class GetAuthInfoFromUUID(ownerUUID: UUID)
	case class Invalidate()
	case class GetAllRuntimes()

	/** These methods are exposed for testing purposes only **/
	case class GetAuthorizationTable()
	case class GetUserTable()
	case class CleanUpRules(list: List[Permission])

	abstract sealed class InvalidationResult
	case class InvalidationComplete() extends InvalidationResult
	case class InvalidationFailed() extends InvalidationResult
}

/**
 * A generic abstract base actor with authentication methods.
 */
abstract class Authenticator(config: CoralConfig)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
	var runtimes: Map[UUID, Runtime] = _
	var permissionHandler: ActorRef = context.actorOf(Props(new PermissionHandler()), "permissionHandler")
	implicit val timeout = Timeout(20.seconds)
	implicit val formats = org.json4s.DefaultFormats
	val cassandra = context.system.actorSelection("/user/root/cassandra")


	override def preStart() = {
		refreshRuntimeTable()
	}

	/**
	 * Obtain a user object from a unique user name. This is common to all authenticators.
	 * @param uniqueUserName The unique name to obtain an AuthInfo object for
	 * @return An optional AuthInfo object if the user was found, None otherwise.
	 */
	def getAuthInfo(uniqueUserName: String): Option[AuthInfo]

	/**
	 * Obtain a user object from a user UUID.
	 * @param userId The user ID to find.
	 * @return An AuthInfo object with the user information belonging to that ID.
	 */
	def getAuthInfo(userId: UUID): Option[AuthInfo]

	/**
	 * Obtain a user object based on its user/password combination as entered
	 * through Basic Auth.
	 * @param up The user/password combination straight from the HTTP header.
	 * @return An AuthInfo object matching the user/password combination, if any.
	 */
	def getAuthInfo(up: UserPass): Option[AuthInfo]

	/**
	 * Main receive loop of the CoralAuthenticator. Does three things:
	 * 1) Checks if a runtime is valid
	 * 2) Returns the authentication info for a certain user/password combination
	 * 3) Invalidates (refreshes) data from Cassandra if requested
	 */
	override def receive = {
		case CheckRuntime(runtimeName, authInfo, cassandraActor) =>
			sender ! checkRuntime(runtimeName, authInfo, cassandraActor)
		case Authenticate(userPass, cassandraActor) =>
			authenticate(userPass)
		case GetOwnerPermissionUUID(runtime: Runtime) =>
			sender ! getOwnerPermissionUUID(runtime)
		case CheckNameAndUser(name, owner) =>
			sender ! checkNameAndUser(name, owner)
		case GetAllRuntimes() =>
			sender ! getAllRuntimes()
		case UserAndRuntimeExist(user, runtime) =>
			sender ! userAndRuntimeExist(user, runtime)
		case GetUserUUIDFromUniqueName(uniqueName) =>
			sender ! getUserUUIDFromUniqueName(uniqueName)
		case GetAuthInfoFromUUID(userUUID) =>
			sender ! getAuthInfo(userUUID)
		case GetAuthorizationTable() =>
			getAuthorizationTable()
		case CleanUpRules(list) =>
			sender ! cleanUpRules(list)
		case Invalidate() =>
			invalidate()
	}

	/**
	 * Invalidates this authenticator.
	 * Refreshes the runtime table.
	 */
	def invalidate() {
		val originalSender = sender()
		log.info("Invalidating cached tables.")
		refreshRuntimeTable()
		originalSender ! InvalidationComplete()
	}

	def refreshRuntimeTable() = {
		// Will have to wait here, cannot continue if runtimes table is not filled...
		val result = Await.result(getRuntimeTable(cassandra),
			timeout.duration).asInstanceOf[Map[UUID, Runtime]]

		log.info(s"Fetched a map of ${result.size} runtimes from database.")
		runtimes = result
	}

	/**
	 * Obtain a list of permissions from database for this user.
	 * This has to be done only once, when the user is logged in for the first time.
	 * @param user The user to get the permissions for
	 * @return A list of permissions if the user is found.
	 */
	def getPermissionsFromDatabase(id: UUID): List[Permission] = {
		// Will have to wait here, sorry
		Await.result(getAuthorizationTable(cassandra, Some(id)), timeout.duration)
	}

	/**
	 * Authentidate a user and return the authorization info for the user.
	 * @param userPass The user pass combination, if any.
	 */
	def authenticate(userPass: Option[UserPass]) {
		userPass match {
			case None => sender ! None
			case Some(up) => sender ! getAuthInfo(up)
		}
	}

	def getAuthorizationTable() {
		val originalSender = sender()
		getAuthorizationTable(cassandra) pipeTo originalSender
	}

	def getAuthorizationTable(cassandra: ActorSelection): Future[List[Permission]] = {
		getAuthorizationTable(cassandra, None)
	}

	/**
	 * Fetches the authorization table from Cassandra and converts
	 * it to a List of Permission objects.
	 * @param cassandra The Cassandra actor to fetch the data from.
	 * @param uniqueName When fetching permissions for a specifc user, use Some(userUUID).
	 *                   When fetching all permissions for all users, use None
	 * @return A list of permission objects.
	 */
	def getAuthorizationTable(cassandra: ActorSelection, userUUID: Option[UUID]): Future[List[Permission]] = {
		log.info("Fetching authorization table...")

		val select = s"""select * from ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable}"""
		val where = if (userUUID.isDefined) s" where user = ${userUUID.get.toString};" else ";"

		val obj: JObject = ("query" -> (select + where))
		val future = cassandra.ask(Shunt(obj)).asInstanceOf[Future[JObject]]
		convertAuthorizationTable(future)
	}

	/**
	 * Fetches the runtime table from Cassandra and converts it to a map of
	 * <runtimeName, Runtime> tuples.
	 * @param cassandra The Cassandra actor to fetch the data from.
	 * @return A map containing <runtimeName, Runtime> tuples.
	 */
	def getRuntimeTable(cassandra: ActorSelection): Future[Map[UUID, Runtime]] = {
		log.info("Fetching runtime table...")

		val json: JObject = ("query" -> (s"""select * from """
			+ s"""${config.coral.cassandra.keyspace}.${config.coral.cassandra.runtimeTable};"""))

		val future = cassandra.ask(Shunt(json)).asInstanceOf[Future[JObject]]
		convertRuntimeTable(future)
	}

	/**
	 * Convert the permission table from a JSON object to a map.
	 * @param jsonTable The JSON table read from Cassandra.
	 * @return A list of permissions for the whole system
	 */
	def convertAuthorizationTable(jsonTable: Future[JObject]): Future[List[Permission]] = {
		jsonTable.map(table => {
			val possiblyConflicting = Permission.fromJson(
				(table \ "data").children.map(_.asInstanceOf[JObject]))
			cleanUpRules(possiblyConflicting)
		})
	}

	/**
	 * Cleans up the rules. Removes exact duplicates, removes
	 * conflicting rules with only allowed different,
	 * and removes rules with empty method, URL, runtimeName or uniqueName.
	 * @param list The list of rules to clean up.
	 * @return A cleaned up list.
	 */
	def cleanUpRules(list: List[Permission]): List[Permission] = {
		// Remove exact duplicates
		var l = list.distinct

		// Remove exact duplicates with different id's
		l = l.foldLeft(List.empty[Permission])((acc, current) => {
			val found = acc.find(p =>
				p.allowed == current.allowed &&
				p.user == current.user &&
				p.method == current.method &&
				p.uri == current.uri &&
				p.runtime == current.runtime
			)

			if (found.isDefined) acc else current :: acc
		}).reverse

		// When the exact same rule with allowed = true
		// and allowed = false exists, allowed = false wins
		l = l.partition(p => {
			val matches = l.exists(p2 =>
				!p2.allowed &&
				p2.user == p.user &&
				p2.method == p.method &&
				p2.uri == p.uri &&
				p2.runtime == p.runtime)

			p.allowed && matches
		})._2

		// Remove rules which have an empty method, URL, runtimeName
		l = l.filter(p => {
			p.method != "" &&
			p.method != null &&
			p.user != null &&
			p.runtime != null &&
			p.uri != null &&
			// Should be bigger than "/api/runtimes/" or "/api/runtimes/*"
			p.uri.length > 14 &&
			p.uri != "/api/runtimes/*"
		})

		l
	}

	/**
	 * Convert the runtime table from a JSON object to a map.
	 * @param jsonTable The JSON table read from Cassandra.
	 * @return A map with <runtimeName, Runtime> tuples.
	 */
	def convertRuntimeTable(jsonTable: Future[JObject]): Future[Map[UUID, Runtime]] = {
		jsonTable.map(table => {
			val runtimes = table \ "data"

			val allRuntimes = runtimes.children.map(runtime => {
				try {
					val id = UUID.fromString((runtime \ "id").extract[String])
					val ownerId = UUID.fromString((runtime \ "owner").extract[String])
					val name = (runtime \ "name").extract[String]
					val adminPath = (runtime \ "adminpath").extract[String]
					val status = (runtime \ "status").extract[Int]

					val projectId = Utils.tryUUID((runtime \ "projectid").extractOpt[String])
					val startedOn = (runtime \ "startedon").extract[Long]
					val jsonDef = parse((runtime \ "jsondef").extract[String]).asInstanceOf[JObject]

					val possibleOwner = getAuthInfo(ownerId)

					possibleOwner match {
						case None =>
							// When the owner is not defined, put a None in the map
							id -> None
						case Some(info) =>
							val (userId, uniqueName) = info.user match {
								// A specific user
								case Left(user) =>
									(user.id, user.uniqueName + "-" + name)
								// The "anonymous/accept all" user
								case Right(acceptAll) =>
									(acceptAll.id, "coral-" + name)
							}

							id -> Some(Runtime(id, userId, name, uniqueName, adminPath, status,
								projectId, jsonDef, None, startedOn))
					}
				} catch {
					case e: Exception => (UUID.randomUUID() -> None)
				}
			}).toMap

			// Filter out any (id -> None) pairs for which the owner is not defined
			for ((key, Some(value)) <- allRuntimes) yield (key -> value)
		})
	}

	/**
	 * Check whether the user UUID and the runtime UUID
	 * exist (in the in-memory state of this actor).
	 * @param user The UUID of the user to find
	 * @param runtime The UUID of the runtime to find
	 * @return True when the user and runtime exist, false otherwise.
	 */
	def userAndRuntimeExist(user: UUID, runtime: UUID): Boolean = {
		val userExists = getAuthInfo(user).isDefined
		val runtimeExists = runtimes.contains(runtime)

		userExists && runtimeExists
	}

	/**
	 * Check if a runtime with the given name and owner was ever deleted.
	 * In this case, it is present in the runtimes table with status == 4.
	 * Return a list of all previous runtime names of the current user.
	 * Also return the user object that is the owner of the runtime, if any.
	 * @param name The name of the runtime
	 * @param owner The owner of the runtime
	 * @return A tuple containing: true/false if it was previously deleted,
	 *         a list of all old runtime names for this user, and Some(user)
	 *         which contains the owner of the runtime.
	 */
	def checkNameAndUser(name: String, owner: UUID): (Boolean, List[String],
			Option[Either[User, AcceptAllUser]]) = {
		log.info("Checking if this runtime was ever deleted...")

		val (everDeleted, oldRuntimes, user) = checkRuntimeStatus(name, owner, _ == 4)

		if (everDeleted) {
			log.warning("This runtime has been deleted before. Changing name to other runtime name.")
		}

		(everDeleted, oldRuntimes, user)
	}

	/**
	 * Checks if the runtime status of a runtime with given name and owner
	 * matches a certain criterium.
	 * @param name The name of the runtime
	 * @param owner The owner of the runtime
	 * @param f The function to check the status against
	 * @return a tuple containing three things:
	 *         1) The function f applied to the runtime status
	 *            For instance, if the function is "_ == 4" then it returns true
	 *            when the status of the runtime is 4, false otherwise.
	 *         2) The name of old runtimes that the user created
	 *         3) The owner matching the owner UUID, which is either a specific user, the "accept all"-user,
	 *            or None if the user cannot be found.
	 */
	def checkRuntimeStatus(name: String, owner: UUID, f: (Int) => Boolean):
		(Boolean, List[String], Option[Either[User, AcceptAllUser]]) = {
		val ownerUser = getAuthInfo(owner)

		val allRuntimes = runtimes.filter(r => {
			r._2.owner == owner
		}).map(_._2).toList

		val runtime = allRuntimes.find(_.name == name)
		val names = allRuntimes.map(_.uniqueName)

		val testedStatus = runtime match {
			case None => false
			case Some(r) => f(r.status)
		}

		val ownerResult = ownerUser match {
			case None => None
			case Some(user) => Some(user.user)
		}

		(testedStatus, names, ownerResult)
	}

	/**
	 * Returns a list of all runtimes currently on the platform.
	 * @return A list with all runtimes
	 */
	def getAllRuntimes(): List[Runtime] = {
		// Do not show deleted runtimes
		runtimes.values.filter(_.status != 4).toList
	}

	/**
	 * Find a runtime by name instead of its unique identifier.
	 * @param user The user that the runtime belongs to. This can also be the "allow all" user.
	 * @param runtimeName The name of the runtime.
	 * @return The runtime, if it can be found
	 */
	def findRuntimeByName(user: AuthInfo, runtimeName: String): Option[Runtime] = {
		user.user match {
			// A specific user
			case Left(u) =>
				// We assume the first part is the user and the second part is the runtime name
				log.info(s"""Getting runtime with user "${u.uniqueName}" and runtime name "$runtimeName"""")

				runtimes.find(r => (r._2.name == runtimeName) && (r._2.owner == u.id)) match {
					case None => None
					case Some(r) => Some(r._2)
				}
			// The "allow all" user
			case Right(all) =>
				// In this case use "coral" as user name
				log.info(s"""Getting runtime with user "${all.name}" and runtime name "$runtimeName"""")
				runtimes.find(r => (r._2.name == runtimeName)) match {
					case None => None
					case Some(r) => Some(r._2)
				}
		}
	}

	/**
	 * Check if the runtime with the given name or UUID exists. If so, return a Runtime object
	 * representing the runtime.
	 * @param runtimeOrUUID The name of the runtime or the UUID of the runtime.
	 * @param authInfo The authorization info containing the
	 *                 login name and details of the current user.
	 * @param cassandra The cassandra actor.
	 * @return Some(runtime) if the runtime was found, None otherwise.
	 */
	def checkRuntime(runtimeOrUUID: String, authInfo: AuthInfo,
							 cassandra: ActorSelection): Option[Runtime] = {
		// First, try if it is a UUID or not
		val uuid = Utils.tryUUID(runtimeOrUUID)

		val result: Option[Runtime] = uuid match {
			case None =>
				if (runtimeOrUUID.contains("-")) {
					// "uniqueOwnerName-runtimeName" is provided
					log.info(s"""Using provided user to obtain runtime "$runtimeOrUUID"""")
					val Array(uniqueUserName, runtimeName) = runtimeOrUUID.split("-")
					val user = getAuthInfo(uniqueUserName)

					if (!user.isDefined) {
						None
					} else {
						findRuntimeByName(user.get, runtimeName)
					}
				} else {
					// If no owner present in the URL, assume the current logged in user
					log.info( s"""Assuming current user for runtime name "$runtimeOrUUID"""")
					val result = findRuntimeByName(authInfo, runtimeOrUUID)
					result
				}
			case Some	(id) =>
				log.info( s"""Getting runtime with UUID $id""")
				val result = runtimes.find(_._2.id == id).map(_._2)
				result
		}

		result match {
			case None =>
				log.error(s"""Runtime "$runtimeOrUUID" not found.""")
				None
			case Some(r) =>
				if (r.status == 4) {
					// Runtime previously deleted, return not found
					None
				} else {
					log.info(s"""Runtime "$runtimeOrUUID" found.""")
					Some(r)
				}
		}
	}

	/**
	 * Find the UUID of the owner permission that belongs to a certain runtime
	 * @param runtime The runtime to get the owner permission for
	 * @return The UUID belonging to the owner permission for the given runtime
	 */
	def getOwnerPermissionUUID(runtime: Runtime): Option[UUID] = {
		for {
			authInfo <- getAuthInfo(runtime.owner)
			ownerPermission <- authInfo.permissions.find(Permission.isOwnerPermission(_, runtime))
		} yield {
			ownerPermission.id
		}
	}

	/**
	 * Get the UUID of a user based on its unique user name.
	 * @param uniqueName The unique name of the user to find
	 * @return Some(uuid) if the user with that name exists, None otherwise
	 */
	def getUserUUIDFromUniqueName(uniqueName: String): Option[UUID] = {
		getAuthInfo(uniqueName) match {
			case Some(AuthInfo(Left(user), _)) =>
				Some(user.id)
			case Some(AuthInfo(Right(allowAll), _)) =>
				Some(allowAll.id)
			case None =>
				None
		}
	}
}