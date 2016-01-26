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
import akka.actor.{Props, ActorSystem, ActorRef, ActorSelection}
import akka.testkit._
import com.github.t3hnar.bcrypt._
import com.typesafe.config.ConfigFactory
import io.coral.TestHelper
import io.coral.TestHelper._
import io.coral.actors.CoralActor.Shunt
import io.coral.actors.RootActor.CreateHelperActors
import io.coral.actors.RootActor
import io.coral.actors.database.CassandraActor
import io.coral.api.{DefaultModule, ApiService, CoralConfig}
import io.coral.api.security.Authenticator.Invalidate
import io.coral.utils.Utils
import org.json4s.JsonAST.{JInt, JField}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http._
import spray.testkit.ScalatestRouteTest
import akka.pattern.ask
import concurrent.duration._
import scala.concurrent.Await

abstract class BaseAuthSpec(mode: String)
	extends WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with BeforeAndAfterEach
	with ScalatestRouteTest
	with ApiService {
	implicit var config: CoralConfig = _
	override def actorRefFactory = system
	implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.second dilated)
	val route = serviceRoute
	val AcceptHeader = RawHeader("Accept", "application/json")
	val ContentTypeHeader = RawHeader("Content-Type", "application/json")
	val JsonApiContentType = ContentType(`application/json`, HttpCharsets.`UTF-8`)
	val duration = timeout.duration
	implicit val injector = new DefaultModule(system.settings.config)
	implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
	implicit var cassandra: TestActorRef[CassandraActor] = _
	var authenticator: ActorSelection = _
	var permissionHandler: ActorSelection = _
	var root: ActorRef = _
	var admin: ActorSelection = _
	var timestamp: Long = System.currentTimeMillis()
	val salt = generateSalt
	val user1 = "ab12cd"
	val pass1 = "pzzwrd"
	val user2 = "cd34ef"
	val pass2 = "encrypted_password2"
	val user3 = "gh56ij"
	val pass3 = "my_pazz"
	val permissionUUID1 = UUID.randomUUID()
	val permissionUUID2 = UUID.randomUUID()
	val permissionUUID3 = UUID.randomUUID()
	val projectUUID1 = UUID.randomUUID()
	var userUUID1 = UUID.randomUUID()
	var userUUID2 = UUID.randomUUID()
	var userUUID3 = UUID.randomUUID()
	var runtimeUUID1: UUID = _
	var runtimeUUID2: UUID = _
	var runtimeUUID3: UUID = _

	override def createActorSystem = {
		val c = createConfig(mode)
		val system = ActorSystem("coral", c.getConfig)
		Utils.setPersistenceSystemProps(c)
		config = c
		system
	}

	def initiateWithConfig(c: CoralConfig) = {
		AuthenticatorChooser.config = Some(c)
		val system = ActorSystem("coral", c.getConfig)
		Utils.setPersistenceSystemProps(c)
		config = c
		system
	}

	override def beforeAll() {
		cassandra = TestHelper.createCassandraActor(
			config.coral.cassandra.contactPoints.head.getHostName,
			config.coral.cassandra.port)
		TestHelper.prepareTables()
		TestHelper.clearAllTables()

		insertUser(userUUID1, "Test user 1", "Department of business", "user1@ing.nl",
			user1, "+3162543556", pass1.bcrypt(salt), timestamp, timestamp)
		insertUser(userUUID2, "Test user 2", "Department of business", "user2@ing.nl",
			user2, "+3162543556", pass2.bcrypt(salt), timestamp, timestamp)
		insertUser(userUUID3, "Test user 3", "Department of business", "user3@ing.nl",
			user3, "+3162543556", pass3.bcrypt(salt), timestamp, timestamp)

		root = TestActorRef[RootActor](Props(new RootActor()), "root")
		root ! CreateHelperActors()

		admin = system.actorSelection("/user/root/admin")
		authenticator = system.actorSelection("/user/root/authenticator")
		permissionHandler = system.actorSelection("/user/root/authenticator/permissionHandler")

		runtimeUUID1 = TestHelper.createStandardRuntime("runtime1", userUUID1, admin)
		runtimeUUID2 = TestHelper.createStandardRuntime("runtime2", userUUID2, admin)
		runtimeUUID3 = TestHelper.createStandardRuntime("runtime3", userUUID3, admin)

		insertPermission(permissionHandler, UUID.randomUUID(), userUUID1, runtimeUUID1, "GET",
			"/api/runtimes/runtime1/actors", allowed = true)

		authenticator ! Invalidate()
		waitAWhile()
	}

	def waitAWhile() = Thread.sleep(100)

	def createConfig(mode: String) = {
		val config = new CoralConfig(ConfigFactory.parseString(
			s"""coral.authentication.mode = "$mode" """).withFallback(
			system.settings.config))
		AuthenticatorChooser.config = Some(config)
		config
	}

	def ownerPermission(method: String, uri: String, allowed: Boolean): Permission = {
		Permission(UUID.randomUUID(), userUUID1, runtimeUUID1, method, uri, allowed)
	}

	def nonOwnerPermission(method: String, uri: String, allowed: Boolean): Permission = {
		Permission(UUID.randomUUID(), userUUID2, runtimeUUID1, method, uri, allowed)
	}

	def checkResponseWithOwner(list: List[Permission], uri: String, code: spray.http.StatusCode) = {
		val l = ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true) :: list
		checkResponse(l, uri, user1, pass1, code)
	}

	def checkResponseWithNonOwner(list: List[Permission], uri: String, code: spray.http.StatusCode) = {
		checkResponse(list, uri, user2, pass2, code)
	}

	def checkResponse(list: List[Permission], uri: String, user: String,
					  password: String, code: spray.http.StatusCode) {
		clearPermissionTable()
		list.foreach(p => TestHelper.insertPermission(permissionHandler, p))

		authenticator ! Invalidate()
		Thread.sleep(100)

		Get(uri).withHeaders(AcceptHeader) ~>
			addCredentials(BasicHttpCredentials(user, password)) ~> sealRoute(route) ~> check {
			assert(status == code)
		}
	}

	def postRuntime(jsonDef: String, expectedStatus: StatusCode): Option[UUID] = {
		Post("/api/runtimes", HttpEntity(MediaTypes.`application/json`, jsonDef))
			.withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == expectedStatus)
			assert(contentType == JsonApiContentType)

			val actual = parse(response.entity.asString).asInstanceOf[JObject]

			if (expectedStatus == StatusCodes.Created) {
				val id = UUID.fromString((actual \ "id").extract[String])
				val expected = parse( s"""{ "success": true, "definition": $jsonDef }""").asInstanceOf[JObject]

				assert(TestHelper.equalWithoutCreatedAndId(actual, expected))
				Some(id)
			} else {
				None
			}
		}
	}

	def runtime1Defined(expectedJson: String, expectedId: UUID) {
		runtimeDefined("runtime1", expectedJson, expectedId)
	}

	def runtime2Defined(expectedJson: String, expectedId: UUID) {
		runtimeDefined("runtime2", expectedJson, expectedId)
	}

	def runtimeDefined(name: String, expectedJson: String, expectedId: UUID) {
		runtimeDefined(name, expectedJson, user1, pass1, expectedId)
	}

	def runtimeDefined(name: String, expectedJson: String, user: String, expectedId: UUID) {}

	def runtimeDefined(name: String, expectedJson: String, user: String, password: String, expectedId: UUID) {
		Get("/api/runtimes/" + name).withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user, password)) ~> route ~> check {
			assert(status == StatusCodes.OK)
			assert(contentType == JsonApiContentType)

			val actual = parse(response.entity.asString).asInstanceOf[JObject]
			assert(UUID.fromString((actual \ "id").extract[String]) == expectedId)

			val uuid: Option[UUID] = try {
				Some(UUID.fromString(name))
			} catch {
				case e: Exception => None
			}

			if (uuid.isDefined) {
				assert(UUID.fromString((actual \ "id").extract[String]) == uuid.get)
			} else if (name.contains("-")) {
				assert((actual \ "name").extract[String] == name.split("-")(1))
				assert((actual \ "uniqueName").extract[String] == name)
			} else {
				assert((actual \ "name").extract[String] == name)
				assert((actual \ "uniqueName").extract[String] == user + "-" + name)
			}

			assert((actual \ "status").extract[String] == "created")
			assert((actual \ "json").extractOpt[JObject].isDefined)

			val json = parse(expectedJson).asInstanceOf[JObject]
			val expectedActors = json \ "actors"
			val expectedLinks = json \ "links"

			val jsonDef = (actual \ "json")
			val actors = (jsonDef \ "actors")
			val links = (jsonDef \ "links")
			assert(actors == expectedActors)
			assert(links == expectedLinks)
		}
	}

	def runtime1NotDefined() {
		runtimeNotDefined("runtime1")
	}

	def runtime2NotDefined() {
		runtimeNotDefined("runtime2")
	}

	def runtimeNotDefined(name: String, user: String, password: String) {
		Get("/api/runtimes/" + name).withHeaders(ContentTypeHeader, AcceptHeader) ~>
			addCredentials(BasicHttpCredentials(user, password)) ~> route ~> check {
			assert(status == StatusCodes.NotFound)
		}
	}

	def runtimeNotDefined(name: String) {
		Get("/api/runtimes/" + name).withHeaders(ContentTypeHeader, AcceptHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == StatusCodes.NotFound)
		}
	}

	def deleteAllRuntimes() {
		Delete(s"/api/runtimes").withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == StatusCodes.OK)
			assert(contentType == JsonApiContentType)
		}
	}

	def deleteRuntime(name: String, expectedStatus: StatusCode) {
		Delete(s"/api/runtimes/" + name).withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == expectedStatus)
			assert(contentType == JsonApiContentType)
		}
	}

	def deleteRuntime1(expectedStatus: StatusCode) {
		deleteRuntime("runtime1", expectedStatus)
	}

	def startRuntime1() {
		startRuntime("runtime1")
	}

	def startRuntime(name: String) {
		statusChange(name, "start")
	}

	def stopRuntime1() {
		stopRuntime("runtime1")
	}

	def stopRuntime(name: String) {
		statusChange(name, "stop")
	}

	def startRuntimeNotFound(name: String) {
		statusChange(name, "start", StatusCodes.NotFound)
	}

	def startRuntime1NotFound() {

	}

	def statusChange(runtime: String, newStatus: String) {
		statusChange(runtime, newStatus, StatusCodes.OK)
	}

	def statusChange(runtime: String, newStatus: String, expectedStatus: StatusCode) {
		val jsonDef = s"""{ "status": "$newStatus" }"""
		Patch("/api/runtimes/" + runtime, HttpEntity(MediaTypes.`application/json`, jsonDef))
			.withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == expectedStatus)
			assert(contentType == JsonApiContentType)
		}
	}

	def getRuntime1Status(): String = {
		getRuntimeStatus("runtime1")
	}

	def getRuntimeStatus(name: String): String = {
		Get("/api/runtimes/" + name).withHeaders(ContentTypeHeader, AcceptHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == StatusCodes.OK)
			val actual = parse(response.entity.asString).asInstanceOf[JObject]
			(actual \ "status").extract[String]
		}
	}

	def only1Runtime1MessageInJournal() {
		RuntimeMessagesInJournal("runtime1", _ == 1)
	}

	def Runtime1MessagesInJournal() {
		RuntimeMessagesInJournal("runtime1", _ > 0)
	}

	def NoRuntimeMessagesInJournal(name: String) {
		RuntimeMessagesInJournal(name, _ == 0)
	}

	def NoRuntime1MessagesInJournal() {
		RuntimeMessagesInJournal("runtime1", _ == 0)
	}

	def RuntimeMessagesInJournal(runtimeName: String, func: (Int) => Boolean) {
		// TODO: partition_nr is not always 0, how to query this?
		val query = s"""select count(*) from akka.journal """ +
			s"""where processor_id = 'ab12cd-$runtimeName' and partition_nr = 0;"""
		val json: JObject = ("query" -> query)
		val result = Await.result(cassandra.ask(Shunt(json)), timeout.duration).asInstanceOf[JObject]
		val find = (result \ "data").findField(_._1 == "count")

		find match {
			case Some(JField("count", JInt(answer))) =>
				assert(func(answer.toInt))
			case _ =>
		}
	}

	def runtime1Status4NotPresent()(implicit cassandra: TestActorRef[CassandraActor]) {
		runtimeStatusPresent(cassandra, "runtime1", userUUID1, _ != 4)
	}

	def runtime1Status4Present(ownerUUID: String) {
		runtimeStatusPresent(cassandra, "runtime1", Utils.tryUUID(ownerUUID).get, _ == 4)
	}

	def runtimeStatusPresent(cassandra: TestActorRef[CassandraActor], name: String,
							 owner: UUID, statusCriterium: (Int) => Boolean) {
		// Unfortunately, there is no primary key on name and owner.
		val query = s"""select * from ${config.coral.cassandra.keyspace}.${config.coral.cassandra.runtimeTable} """
		val json: JObject = ("query" -> query)
		val result = Await.result(cassandra.ask(Shunt(json)), timeout.duration).asInstanceOf[JObject]

		if ((result \ "data").children.size == 0) {
			assert(false)
		} else {
			val data = (result \ "data")
			val matches = data.children.filter(f => {
				val foundName = (f \ "name").extractOrElse("")
				val foundOwner = UUID.fromString((f \ "owner").extract[String])
				foundName == name && foundOwner == owner
			})

			if (matches.size == 0) {
				assert(false, "No matches found for runtime criteria")
			} else {
				val find = matches(0).asInstanceOf[JObject]
				val findField = find.findField(_._1 == "status")

				val answer = findField match {
					case Some(JField("status", JInt(status))) => status.toInt
					case _ => 0
				}

				assert(statusCriterium(answer))
			}
		}
	}

	def user1HasNoAccessToRuntime1() {
		userHasNoAccess(user1, pass1, "runtime1")
	}

	def userHasNoAccess(user: String, pass: String, runtime: String){
		Get(s"/api/runtimes/$runtime/actors").withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user, pass)) ~> sealRoute(route) ~> check {
			assert(status == StatusCodes.Forbidden)
		}
	}

	def permissionNotFound(runtimeName: String, id: UUID) = {
		Get(s"/api/runtimes/$runtimeName/permissions/" + id.toString).withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == StatusCodes.NotFound)
		}
	}

	def permissionNotFound(id: UUID) {
		permissionNotFound("runtime1", id)
	}

	def addPermission(userId: UUID, runtimeId: UUID, allowed: Boolean): UUID = {
		addPermission(userId, "runtime1", runtimeId, allowed)
	}

	def addPermission(userId: UUID, runtimeName: String, runtimeId: UUID, allowed: Boolean): UUID = {
		val jsonDef = s"""{
						 |  "id": "${UUID.randomUUID().toString}",
						 |  "user": "${userId.toString}",
						 |  "runtime": "${runtimeId.toString}",
						 |  "method": "GET",
						 |  "uri": "/api/runtimes/$runtimeName/actors",
						 |  "allowed": $allowed
						 |}""".stripMargin

		Post(s"/api/runtimes/$runtimeName/permissions", HttpEntity(MediaTypes.`application/json`, jsonDef))
			.withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == StatusCodes.Created)
			assert(contentType == JsonApiContentType)

			val actual = parse(response.entity.asString).asInstanceOf[JObject]
			UUID.fromString((actual \ "id").extract[String])
		}
	}

	def user1HasAccessToRuntime1() {
		userHasAccess(user1, pass1, "runtime1")
	}

	def userHasAccess(user: String, pass: String, runtime: String) {
		Get(s"/api/runtimes/$runtime/actors").withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user, pass)) ~> route ~> check {
			assert(status != StatusCodes.Unauthorized && status != StatusCodes.Forbidden)
			assert(contentType == JsonApiContentType)
		}
	}

	def permissionFound(runtime: String, permissionId: UUID) {
		permissionFound(user1, pass1, runtime, permissionId)
	}

	def permissionFound(permissionId: UUID) {
		permissionFound(user1, pass1, "runtime1", permissionId)
	}

	def permissionFound(user: String, pass: String, runtime: String, permissionId: UUID) {
		Get(s"/api/runtimes/${runtime}/permissions/" + permissionId.toString).withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user, pass)) ~> route ~> check {
			assert(status == StatusCodes.OK)
			assert(contentType == JsonApiContentType)
		}
	}

	def updatePermission(runtimeName: String, id: UUID, allowed: Boolean) {
		val jsonDef = s"""{ "id": "${id.toString}", "allowed": $allowed }"""

		Patch(s"/api/runtimes/$runtimeName/permissions/" + id.toString, HttpEntity(MediaTypes.`application/json`, jsonDef))
			.withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == StatusCodes.OK)
			assert(contentType == JsonApiContentType)
		}
	}

	def updatePermission(id: UUID, allowed: Boolean) {
		updatePermission("runtime1", id, allowed)
	}

	def removePermission(runtimeName: String, id: UUID) = {
		Delete(s"/api/runtimes/$runtimeName/permissions/" + id.toString).withHeaders(AcceptHeader, ContentTypeHeader) ~>
			addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
			assert(status == StatusCodes.OK)
			assert(contentType == JsonApiContentType)
		}
	}

	def removePermission(id: UUID) {
		removePermission("runtime1", id)
	}
}