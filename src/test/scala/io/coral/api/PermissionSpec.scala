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

package io.coral.api

import com.github.t3hnar.bcrypt._
import akka.testkit.{TestKit, TestActorRef}
import com.typesafe.config.ConfigFactory
import io.coral.TestHelper
import io.coral.TestHelper._
import io.coral.actors.RootActor.{CreateHelperActors, CreateTestActors}
import io.coral.actors.RootActor
import io.coral.api.security.Authenticator.Invalidate
import io.coral.api.security.BaseAuthSpec
import spray.http.StatusCodes

/**
 * Class to test permission handling.
 * Also checks actual access with a user based on these permissions.
 * Not tested in ApiServiceSpec because that class assumes the
 * "accept all" user and that kind of defeats the purpose of permissions.
 *
 * Using authentication mechanism "coral" but should also work with LDAP.
 */
class PermissionSpec extends BaseAuthSpec("coral") {
	override def createActorSystem = {
		val c = new CoralConfig(ConfigFactory.parseString(
			s"""coral.authentication.mode = "coral"
			| akka.actor.provider = "akka.actor.LocalActorRefProvider"
			| coral.cluster.enable = false""".stripMargin)
			.withFallback(ConfigFactory.load()))
		initiateWithConfig(c)
	}

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	override def beforeAll() {
		root = TestActorRef[RootActor](new RootActor(), "root")

		root ! CreateHelperActors()
		Thread.sleep(1000)

		admin = system.actorSelection("/user/root/admin")

		cassandra = TestHelper.createCassandraActor(
			config.coral.cassandra.contactPoints.head.getHostName,
			config.coral.cassandra.port)
		TestHelper.prepareTables()
		TestHelper.clearAllTables()

		permissionHandler = system.actorSelection("/user/root/authenticator/permissionHandler")
		authenticator = system.actorSelection("/user/root/authenticator")

		insertUser(userUUID1, "Test user 1", "Department of business", "user1@ing.nl",
			user1, "+3162543556", pass1.bcrypt(salt), timestamp, timestamp)
		insertUser(userUUID2, "Test user 2", "Department of business", "user2@ing.nl",
			user2, "+3162543556", pass2.bcrypt(salt), timestamp, timestamp)
		insertUser(userUUID3, "Test user 3", "Department of business", "user3@ing.nl",
			user3, "+3162543556", pass3.bcrypt(salt), timestamp, timestamp)

		authenticator ! Invalidate()
		Thread.sleep(500)
	}

	override def beforeEach() {
		TestHelper.beforeEach()
	}

	"adding and removing permissions" should {
		"add a permission" in {
			val name = "runtime1"
			val uuid = postRuntime(TestHelper.json(name, userUUID1.toString), StatusCodes.Created)
			assert(uuid.isDefined)
			waitAWhile()
			user1HasAccessToRuntime1()

			val permissionId = addPermission(userUUID1, uuid.get, allowed = false)
			waitAWhile()
			permissionFound(permissionId)
			user1HasNoAccessToRuntime1()
		}

		"get a permission" in {
			val name = "runtime10"

			val uuid = postRuntime(TestHelper.json(name, userUUID1.toString), StatusCodes.Created)
			waitAWhile()
			assert(uuid.isDefined)

			val permissionId = addPermission(userUUID1, name, uuid.get, allowed = true)
			waitAWhile()
			permissionFound(name, permissionId)
		}

		"update a permission" in {
			val name = "runtime20"

			val uuid = postRuntime(TestHelper.json(name, userUUID1.toString), StatusCodes.Created)
			waitAWhile()
			assert(uuid.isDefined)
			userHasAccess(user1, pass1, name)

			val permissionId = addPermission(userUUID1, name, uuid.get, allowed = false)
			waitAWhile()
			permissionFound(name, permissionId)
			userHasNoAccess(user1, pass1, name)

			updatePermission(name, permissionId, allowed = true)
			waitAWhile()
			permissionFound(name, permissionId)
			userHasAccess(user1, pass1, name)
		}

		"remove a permission" in {
			val name = "runtime30"

			val uuid = postRuntime(TestHelper.json(name, userUUID1.toString), StatusCodes.Created)
			waitAWhile()
			assert(uuid.isDefined)
			userHasAccess(user1, pass1, name)

			val permissionId = addPermission(userUUID1, name, uuid.get, allowed = false)
			waitAWhile()
			userHasNoAccess(user1, pass1, name)
			permissionFound(name, permissionId)
			removePermission(name, permissionId)
			waitAWhile()

			permissionNotFound(name, permissionId)
			userHasAccess(user1, pass1, name)
		}
	}
}