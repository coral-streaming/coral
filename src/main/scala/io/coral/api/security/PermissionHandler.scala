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
import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.util.Timeout
import io.coral.actors.CoralActor.Shunt
import io.coral.api.security.Authenticator.{Invalidate, GetOwnerPermissionUUID}
import io.coral.api.{Runtime, CoralConfig}
import org.json4s.{JArray, JObject}
import scala.concurrent.{ExecutionContext, Future}
import akka.pattern.ask
import org.json4s.JsonDSL._
import scala.concurrent.duration._
import akka.pattern.pipe

case class AddPermission(json: JObject)
case class AddOwnerPermission(runtime: Option[Runtime])
case class RemoveOwnerPermission(runtime: Option[Runtime])
case class RemovePermission(user: UUID, permissionId: UUID)
case class UpdatePermission(userId: UUID, permissionId: UUID, allowed: Boolean)
case class GetPermission(userUUID: UUID, id: UUID)
case class InsertAdmin()
case class AddAdmin(user: User)

/**
 * Actor that adds, deletes, updates and reads permissions.
 * This actor is always a child of CoralAuthenticator.
 */
class PermissionHandler(implicit ec: ExecutionContext) extends Actor with ActorLogging {
	val cassandra = context.actorSelection("/user/root/cassandra")
	val config = new CoralConfig(context.system.settings.config)
	implicit val timeout = Timeout(2.seconds)
	implicit val formats = org.json4s.DefaultFormats

	override def receive = {
		case AddPermission(json: JObject) =>
			addPermission(json, sender())
		case AddOwnerPermission(runtime: Option[Runtime]) =>
			addOwnerPermission(runtime, sender())
		case RemoveOwnerPermission(runtime) =>
			removeOwnerPermission(runtime)
		case RemovePermission(user: UUID, permissionId: UUID) =>
			deletePermission(user, permissionId, sender())
		case UpdatePermission(userId, permissionId, allowed) =>
			updatePermission(userId, permissionId, allowed)
		case GetPermission(userUUID: UUID, id: UUID) =>
			getPermission(userUUID, id)
		case AddAdmin(user) =>
			addAdmin(user)
	}

	/**
	 * Adds an admin user to the database. The first user that gets added to
	 * the platform must be added this way. This method is called from the
	 * command line function "user add". Other users can be added through the
	 * web API.
	 * @param u The user to add as administrator.
	 */
	def addAdmin(u: User) {
		val originalSender = sender()

		// Make sure it is unique
		val query = s"""select * from ${config.coral.cassandra.keyspace}.""" +
			s"""${config.coral.cassandra.userTable} where uniquename = '${u.uniqueName}'"""
		val json1: JObject = ("query" -> query)

		cassandra.ask(Shunt(json1)).asInstanceOf[Future[JObject]].flatMap(result => {
			val count = (result \ "data").extract[JArray].children.size

			if (count == 0) {
				log.info(s"""Inserting administrator "${u.uniqueName}" into database.""")

				val stmt = s"""insert into ${config.coral.cassandra.keyspace}.${config.coral.cassandra.userTable}""" +
					s""" (id, fullname, department, email, uniquename, mobilephone,
					   |hashedpassword, createdon, lastlogin) values (
					   |${u.id.toString}, '${u.fullName}', '${u.department.getOrElse("")}',
					   |'${u.email}', '${u.uniqueName}', '${u.mobilePhone.getOrElse("")}',
					   |'${u.hashedPassword}', ${u.createdOn}, ${u.lastLogin.getOrElse(0)})""".stripMargin
				val json2: JObject = ("query" -> stmt)

				cassandra.ask(Shunt(json2)).asInstanceOf[Future[JObject]].map(result => {
					val success = (result \ "success").extract[Boolean]
					("action" -> "Add user") ~
					("success" -> success)
				})
			} else {
				val message = s"""User with unique name "${u.uniqueName}" already exists."""
				log.error(message)
				Future.successful(
					("action" -> "Add user") ~
					("success" -> false) ~
					("reason" -> message))
			}
		}) pipeTo originalSender
	}

	/**
	 * Add a permission for a runtime.
	 * @return The result of the operation.
	 */
	def addPermission(json: JObject, sender: ActorRef) {
		val p = Permission.fromJson(json)
		addPermission(p, sender)
	}

	/**
	 * Add a permission based on a Permission object.
	 */
	def addPermission(p: Permission, sender: ActorRef): Future[JObject] = {
		log.info(s"""Adding permission $p for runtime "${p.runtime}"""")
		val stmt = s"""insert into ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable}""" +
			s""" (id, user, runtime, method, uri, allowed) values (""" +
			s"""${p.id}, ${p.user}, ${p.runtime}, '${p.method}', '${p.uri}', ${p.allowed});"""
		val json: JObject = ("query" -> stmt)

		val future = cassandra.ask(Shunt(json)).asInstanceOf[Future[JObject]].map(result => {
			val authenticator = context.actorSelection("/user/root/authenticator")
			authenticator ! Invalidate()

			val success = (result \ "success").extract[Boolean]

			("action" -> "Add permission") ~
			("id" -> p.id.toString) ~
			("success" -> success)
		})

		future pipeTo sender
		future
	}

	/**
	 * Delete a permission with the given UUID from the permission table.
	 * @param owner the UUID of the user owner.
	 * @param permissionId The UUID of the permission to delete.
	 * @return The result of the action
	 */
	def deletePermission(owner: UUID, permissionId: UUID, sender: ActorRef) {
		log.info(s"""Deleting permission from user ${owner.toString} with id ${permissionId.toString}""")
		val stmt = s"""delete from ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable} """ +
			s"""where user = ${owner.toString} and id = ${permissionId.toString};"""
		val json: JObject = ("query" -> stmt)

		cassandra.ask(Shunt(json)).asInstanceOf[Future[JObject]].map(result => {
			val authenticator = context.actorSelection("/user/root/authenticator")
			authenticator ! Invalidate()

			val success = (result \ "success").extract[Boolean]

			("action" -> "Delete permission") ~
			("owner" -> owner.toString) ~
			("id" -> permissionId.toString) ~
			("success" -> success)
		}) pipeTo sender
	}

	/**
	 * Update a permission. Can only set allowed to true or false,
	 * the rest cannot be changed after creating it.
	 * @param userId The UUID of the user to which the permission belongs.
	 * @param id The UUID of the permission.
	 * @param allowed The value of allowed to set
	 * @return An object stating the result of the action
	 */
	def updatePermission(userId: UUID, permissionId: UUID, allowed: Boolean) {
		val originalSender = sender()

		log.info(s"""Updating permission with id ${permissionId.toString} from user with id ${userId.toString}""")
		val stmt = s"""update ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable} """ +
				   s"""set allowed = $allowed where user = ${userId.toString} and id = ${permissionId.toString};"""
		val json: JObject = ("query" -> stmt)

		cassandra.ask(Shunt(json)).asInstanceOf[Future[JObject]].map(result => {
			val authenticator = context.actorSelection("/user/root/authenticator")
			authenticator ! Invalidate()

			val success = (result \ "success").extract[Boolean]

			("action" -> "Update permission") ~
			("allowed" -> allowed) ~
			("success" -> success)
		}) pipeTo originalSender
	}

	/**
	 * Gets a permission object for a given permission ID.
	 * @param userUUID The ID of the user to which the permission belongs.
	 * @param id The ID of the permission
	 * @return A future with the complete permission object
	 */
	def getPermission(userUUID: UUID, id: UUID) {
		val originalSender = sender()

		log.info(s"""Getting permission with id ${id.toString}""")
		val stmt = s"""select * from ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable} """ +
				   s"""where user = ${userUUID.toString} and id = ${id.toString}"""
		val json: JObject = ("query" -> stmt)

		cassandra.ask(Shunt(json)).asInstanceOf[Future[JObject]].map(result => {
			val array = (result \ "data").asInstanceOf[JArray]

			if (array.arr.size == 0) {
				("action" -> "Get permission") ~
				("success" -> false) ~
				("reason" -> "No data returned from permission table")
			} else {
				array.arr(0).asInstanceOf[JObject]
			}
		}) pipeTo originalSender
	}

	/**
	 * Insert the ownership permission in the runtime table. The ownership permission is
	 * defined as follows:
	 *
	 * owner: <uniqueName>
	 * name: runtimeName
	 * method: "*"
	 * URL: "/api/runtimes/<runtimeName>/<star>"
	 * allowed: true
	 */
	def addOwnerPermission(runtime: Option[Runtime], sender: ActorRef) {
		runtime match {
			case None =>
				val message = "Cannot add owner permission for non-existing runtime"
				log.error(message)
				sender !
					("action" -> "Add owner permission") ~
					("success" -> false) ~
					("reason" -> message)
			case Some(r) =>
				val ownerPermission = Permission(
					UUID.randomUUID(),
					r.owner,
					r.id,
					"*",
					s"/api/runtimes/${r.name}/*",
					allowed = true)
				addPermission(ownerPermission, sender)
		}
	}

	/**
	 * Removes the owner permissions from the authorization table.
	 * @param runtime The runtime to remove the permissions for.
	 */
	def removeOwnerPermission(runtime: Option[Runtime]) {
		val originalSender = sender()

		runtime match {
			case None =>
				val message = "Cannot remove owner permission for a nonexisting runtime"
				log.error(message)
				originalSender !
					("action" -> "Remove owner permission") ~
					("success" -> false) ~
					("reason" -> message)
			case Some(r) =>
				log.warning(s"Removing owner permission")
				context.parent.ask(GetOwnerPermissionUUID(r))
					.asInstanceOf[Future[Option[UUID]]].map(_ match {
						case None =>
							val message = "Did not obtain UUID for owner permission"
							log.error(message)
							originalSender !
								("action" -> "Remove owner permission") ~
								("success" -> false) ~
								("reason" -> message)
						case Some(p) =>
							deletePermission(r.owner, p, originalSender)
					}
				)
		}
	}
}