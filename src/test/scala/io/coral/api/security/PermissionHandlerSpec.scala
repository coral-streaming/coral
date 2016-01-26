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
import akka.actor.Props
import akka.testkit.{TestProbe, TestActorRef}
import com.github.t3hnar.bcrypt._
import com.typesafe.config.ConfigFactory
import io.coral.TestHelper
import io.coral.TestHelper._
import io.coral.actors.CoralActor.Shunt
import io.coral.actors.RootActor
import io.coral.actors.RootActor.CreateHelperActors
import io.coral.api.security.Authenticator.Invalidate
import io.coral.api.{CoralConfig, Runtime}
import io.coral.cluster.MachineAdmin
import org.json4s.JObject
import scala.concurrent.Await
import org.json4s.jackson.JsonMethods._
import akka.pattern.ask
import org.json4s._
import org.json4s.JsonDSL._

class PermissionHandlerSpec
	extends BaseAuthSpec("coral") {
	override def createActorSystem = {
		val c = new CoralConfig(ConfigFactory.parseString(
			s"""coral.authentication.mode = "coral"
	   		|akka.actor.provider = "akka.actor.LocalActorRefProvider"
	   		|coral.cluster.enable = false""".stripMargin)
			.withFallback(ConfigFactory.load()))
		initiateWithConfig(c)
	}

	var runtime1 = Runtime(UUID.randomUUID(), userUUID1,
		"runtime1", "ab12cd-runtime1",
		"akka.tcp://coral@127.0.0.1:2551/user/root/admin",
		0, Some(UUID.randomUUID()), null, None, 0)

	override def beforeAll() {
		root = TestActorRef[RootActor](new RootActor(), "root")
		root ! CreateHelperActors()
		admin = system.actorSelection("/user/root/admin")

		cassandra = TestHelper.createCassandraActor(
			config.coral.cassandra.contactPoints.head.getHostName,
			config.coral.cassandra.port)
		TestHelper.prepareTables()

		insertUser(userUUID1, "Test user 1", "Department of business", "user1@ing.nl",
			user1, "+3162543556", pass1.bcrypt(salt), timestamp, timestamp)
		insertUser(userUUID2, "Test user 2", "Department of business", "user2@ing.nl",
			user2, "+3162543556", pass2.bcrypt(salt), timestamp, timestamp)
		insertUser(userUUID3, "Test user 3", "Department of business", "user3@ing.nl",
			user3, "+3162543556", pass3.bcrypt(salt), timestamp, timestamp)

		authenticator = system.actorSelection("/user/root/authenticator")
		permissionHandler = system.actorSelection("/user/root/authenticator/permissionHandler")

		authenticator ! Invalidate()
		Thread.sleep(500)
	}

	"A permissionHandler actor" should {
		"add an owner permission" in {
			TestHelper.clearPermissionTable()

			permissionHandler ! AddOwnerPermission(Some(runtime1))

			Thread.sleep(1000)
			val fromDatabase = getSinglePermissionFromDatabase()

			assert(fromDatabase.allowed)
			assert(fromDatabase.method == "*")
			assert(fromDatabase.user == runtime1.owner)
			assert(fromDatabase.runtime == runtime1.id)
			assert(fromDatabase.uri == s"/api/runtimes/${runtime1.name}/*")
		}

		"add a permission" in {
			TestHelper.clearPermissionTable()
			val json = parse(s"""{
				|   "id": "${UUID.randomUUID().toString}",
				|   "user": "${runtime1.owner.toString}",
				|   "runtime": "${runtime1.id.toString}",
				|   "method": "GET",
				|   "uri": "/api/runtimes/runtime1/actors/some/path",
				|   "allowed": true
				| }""".stripMargin).asInstanceOf[JObject]

			val result = Await.result(permissionHandler.ask(AddPermission(json)),
				timeout.duration).asInstanceOf[JObject]
			assert((result \ "success").extract[Boolean] == true)
			val fromDatabase = getSinglePermissionFromDatabase()

			assert(fromDatabase.allowed)
			assert(fromDatabase.method == "GET")
			assert(fromDatabase.user == runtime1.owner)
			assert(fromDatabase.runtime == runtime1.id)
			assert(fromDatabase.uri == s"/api/runtimes/${runtime1.name}/actors/some/path")
		}

		"remove an owner permission" in {
			TestHelper.clearPermissionTable()

			permissionHandler ! AddOwnerPermission(Some(runtime1))
			Thread.sleep(500)

			authenticator ! Invalidate()

			val result = Await.result(permissionHandler.ask(RemoveOwnerPermission(Some(runtime1))),
				timeout.duration).asInstanceOf[JObject]

			assert((result \ "success").extract[Boolean] == true)

			authenticator ! Invalidate()
			Thread.sleep(500)

			permissionTableIsEmpty()
		}

		"remove a permission" in {
			val permissionId = UUID.randomUUID()

			val json = parse(s"""{
				|   "id": "${permissionId.toString}",
				|   "user": "${runtime1.owner.toString}",
				|   "runtime": "${runtime1.id.toString}",
				|   "method": "GET",
				|   "uri": "/api/runtimes/runtime1/actors",
				|   "allowed": true
				| }""".stripMargin).asInstanceOf[JObject]

			val result1 = Await.result(permissionHandler.ask(AddPermission(json)),
				timeout.duration).asInstanceOf[JObject]
			assert((result1 \ "success").extract[Boolean] == true)

			authenticator ! Invalidate()
			Thread.sleep(100)

			val result2 = Await.result(permissionHandler.ask(RemovePermission(runtime1.owner, permissionId)),
				timeout.duration).asInstanceOf[JObject]

			assert((result2 \ "success").extract[Boolean] == true)

			authenticator ! Invalidate()
			Thread.sleep(100)

			permissionTableIsEmpty()
		}

		"get a permission" in {
			val permissionId = UUID.randomUUID()

			val json = parse(s"""{
				|   "id": "${permissionId.toString}",
				|   "user": "${runtime1.owner.toString}",
				|   "runtime": "${runtime1.id.toString}",
				|   "method": "GET",
				|   "uri": "/api/runtimes/runtime1/actors",
				|   "allowed": true
				| }""".stripMargin).asInstanceOf[JObject]

			val result1 = Await.result(permissionHandler.ask(AddPermission(json)),
				timeout.duration).asInstanceOf[JObject]
			assert((result1 \ "success").extract[Boolean] == true)

			authenticator ! Invalidate()
			Thread.sleep(100)

			val actual = Await.result(permissionHandler.ask(GetPermission(runtime1.owner, permissionId)),
				timeout.duration).asInstanceOf[JObject]

			assert(actual == json)
		}

		"update a permission" in {
			val permissionId = UUID.randomUUID()

			val json = parse(s"""{
				|   "id": "${permissionId.toString}",
				|   "user": "${runtime1.owner.toString}",
				|   "runtime": "${runtime1.id.toString}",
				|   "method": "GET",
				|   "uri": "/api/runtimes/runtime1/actors",
				|   "allowed": true
				| }""".stripMargin).asInstanceOf[JObject]

			val result1 = Await.result(permissionHandler.ask(AddPermission(json)),
				timeout.duration).asInstanceOf[JObject]
			assert((result1 \ "success").extract[Boolean] == true)

			authenticator ! Invalidate()
			Thread.sleep(100)

			val result2 = Await.result(permissionHandler.ask(
				UpdatePermission(runtime1.owner, permissionId, allowed = false)),
				timeout.duration).asInstanceOf[JObject]
			assert((result2 \ "success").extract[Boolean] == true)

			authenticator ! Invalidate()
			Thread.sleep(100)

			val result3 = Await.result(permissionHandler.ask(GetPermission(runtime1.owner, permissionId)),
				timeout.duration).asInstanceOf[JObject]
			assert((result3 \ "allowed").extract[Boolean] == false)
		}

		def getSinglePermissionFromDatabase(): Permission = {
			val query = s"select * from ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable};"
			val json: JObject = ("query" -> query)
			val permission = (Await.result(cassandra.ask(Shunt(json)),
				timeout.duration).asInstanceOf[JObject] \ "data").children(0).asInstanceOf[JObject]
			Permission.fromJson(permission)
		}

		def permissionTableIsEmpty() {
			val query = s"select count(*) from ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable};"
			val json: JObject = ("query" -> query)
			val answer = Await.result(cassandra.ask(Shunt(json)),
				timeout.duration).asInstanceOf[JObject]
			val count = ((answer \ "data").children(0) \ "count").extract[Int]
			assert(count == 0)
		}
	}
}