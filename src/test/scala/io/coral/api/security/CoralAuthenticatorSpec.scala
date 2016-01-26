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
import akka.testkit.{TestActorRef, TestKit}
import com.typesafe.config.ConfigFactory
import io.coral.TestHelper
import io.coral.api.CoralConfig
import io.coral.api.security.Authenticator.{GetUserTable, CleanUpRules, GetAuthorizationTable, Invalidate}
import spray.http.{BasicHttpCredentials, StatusCodes}
import com.github.t3hnar.bcrypt._
import io.coral.TestHelper._
import scala.concurrent.Await
import akka.pattern.ask

class CoralAuthenticatorSpec
	extends AuthenticatorSpec("coral") {
	override def createActorSystem = {
		val c = new CoralConfig(ConfigFactory.parseString(
			s"""coral.authentication.mode = "coral"
			   |akka.actor.provider = "akka.actor.LocalActorRefProvider"
			   |coral.cluster.enable = false
			 """.stripMargin)
			.withFallback(ConfigFactory.load()))
		initiateWithConfig(c)
	}

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"A CoralAuthenticator actor" when {
		"Allow multiple sub-paths with a wildcard" in {
			checkResponseWithNonOwner(List(
				nonOwnerPermission("GET", "/api/runtimes/ab12cd-runtime1/*", allowed = true)),
				"/api/runtimes/ab12cd-runtime1/actors",
				StatusCodes.OK)

			Get("/api/runtimes/runtime1/actors/generator1").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user2, pass2)) ~> sealRoute(route) ~> check {
				// The actual actor does not exist but that is OK as long as it is not forbidden
				assert(status != StatusCodes.Forbidden)
			}
		}

		"A non-runtime owner cannot find a runtime created by another user by default" in {
			// We start with 3 runtimes already defined but
			// clear them out here since we want to start over
			deleteAllRuntimes()

			// Create runtime runtime1 by user ab12cd
			// Access it through /api/runtimes/ab12cd-runtime1/actors with user ab12cd => OK
			// Access it through /api/runtimes/runtime1/actors with user ab12cd => OK
			// Access it through /api/runtimes/runtime1/actors with user cd34ef => Not found
			// Grant access to /api/runtimes/runtime1/actors with user cd34ef
			// Access it through /api/runtimes/ab12cd-runtime1/actors with user cd34ef => OK
			runtime1NotDefined()
			val jsonDef = TestHelper.json(userUUID1, "someruntime")
			val uuid1 = postRuntime(jsonDef, StatusCodes.Created)
			waitAWhile()

			assert(uuid1.isDefined)
			runtimeDefined("someruntime", jsonDef, uuid1.get)
			runtimeDefined("ab12cd-someruntime", jsonDef, uuid1.get)
			runtimeDefined("someruntime", jsonDef, uuid1.get)
			runtimeNotDefined("someruntime", user2, pass2)

			val permissionHandler = system.actorSelection("/user/root/authenticator/permissionHandler")
			TestHelper.insertPermission(permissionHandler, UUID.randomUUID(), userUUID2, uuid1.get,
				"GET", "/api/runtimes/ab12cd-someruntime", allowed = true)
			authenticator ! Invalidate()
			waitAWhile()

			runtimeNotDefined("someruntime", user2, pass2)
			runtimeDefined("ab12cd-someruntime", jsonDef, user2, pass2, uuid1.get)
		}

		"Return a filled user table" in {
			val actual = Await.result(authenticator.ask(GetUserTable()), timeout.duration)

			val expected = Map[String, User](
				user1 -> User(
					userUUID1,
					"Test user 1",
					Some("Department of business"),
					"user1@ing.nl",
					user1,
					Some("+3162543556"),
					pass1.bcrypt(salt),
					timestamp,
					Some(timestamp)
				), user2 -> User(
					userUUID2,
					"Test user 2",
					Some("Department of business"),
					"user2@ing.nl",
					user2,
					Some("+3162543556"),
					pass2.bcrypt(salt),
					timestamp,
					Some(timestamp)
				), user3 -> User(
					userUUID3,
					"Test user 3",
					Some("Department of business"),
					"user3@ing.nl",
					user3,
					Some("+3162543556"),
					pass3.bcrypt(salt),
					timestamp,
					Some(timestamp)
				)
			)

			assert(actual == expected)
		}

		"Return a filled authorization list" in {
			TestHelper.clearAllTables()

			val id1 = UUID.randomUUID()
			val id2 = UUID.randomUUID()
			val id3 = UUID.randomUUID()

			insertPermission(permissionHandler, id1, userUUID1,
				runtimeUUID1, "*", "/api/runtimes/runtime1/*", allowed = true)
			insertPermission(permissionHandler, id2, userUUID2,
				runtimeUUID2, "POST", "/api/runtimes/runtime1/actors/actor1/shunt", allowed = false)
			insertPermission(permissionHandler, id3, userUUID3,
				runtimeUUID3, "DELETE", "/api/runtimes/runtime1/actors/1", allowed = false)

			authenticator ! Invalidate()

			val actual = Await.result(authenticator.ask(GetAuthorizationTable()), timeout.duration)
				.asInstanceOf[List[Permission]].sortBy(_.id)

			val expected = List(
				Permission(id1, userUUID1, runtimeUUID1, "*",
					"/api/runtimes/runtime1/*", allowed = true),
				Permission(id2, userUUID2, runtimeUUID2, "POST",
					"/api/runtimes/runtime1/actors/actor1/shunt", allowed = false),
				Permission(id3, userUUID3, runtimeUUID3, "DELETE",
					"/api/runtimes/runtime1/actors/1", allowed = false)).sortBy(_.id)

			assert(actual == expected)
		}

		"Filter the list with duplicate values" in {
			val list: List[Permission] = List(
				Permission(permissionUUID1, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(UUID.randomUUID(), userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(UUID.randomUUID(), userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(UUID.randomUUID(), userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(permissionUUID2, userUUID2, runtimeUUID2,
					"GET", "/api/runtimes/runtime2/actors", allowed = true))

			val actual = Await.result(authenticator.ask(CleanUpRules(list)), timeout.duration)

			val expected: List[Permission] = List(
				Permission(permissionUUID1, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(permissionUUID2, userUUID2, runtimeUUID2,
					"GET", "/api/runtimes/runtime2/actors", allowed = true))

			assert(actual == expected)
		}

		"Filter the list with allowed = true and allowed = false for the same rule" in {
			var list: List[Permission] = List(
				Permission(permissionUUID1, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(permissionUUID2, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = false))

			var actual = Await.result(authenticator.ask(CleanUpRules(list)), timeout.duration)

			var expected: List[Permission] = List(
				Permission(permissionUUID2, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = false))

			assert(actual == expected)

			list = List(
				Permission(permissionUUID1, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = false),
				Permission(permissionUUID2, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true))

			actual = Await.result(authenticator.ask(CleanUpRules(list)), timeout.duration)

			expected = List(
				Permission(permissionUUID1, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = false))

			assert(actual == expected)

			val uuid3 = UUID.randomUUID()

			list = List(
				Permission(permissionUUID1, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = false),
				Permission(permissionUUID2, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(permissionUUID3, userUUID2, runtimeUUID1,
					"POST", "/api/runtimes/1", allowed = true))

			actual = Await.result(authenticator.ask(CleanUpRules(list)), timeout.duration)

			expected = List(
				Permission(permissionUUID1, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = false),
				Permission(permissionUUID3, userUUID2, runtimeUUID1,
					"POST", "/api/runtimes/1", allowed = true))

			assert(actual == expected)
		}

		"Filter the list with duplicate values and allowed = true and allowed = false" in {
			val list: List[Permission] = List(
				Permission(permissionUUID1, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(permissionUUID2, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = true),
				Permission(permissionUUID3, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = false))

			val actual = Await.result(authenticator.ask(CleanUpRules(list)), timeout.duration)

			val expected: List[Permission] = List(
				Permission(permissionUUID3, userUUID1, runtimeUUID1,
					"GET", "/api/runtimes/runtime1/actors", allowed = false))

			assert(actual == expected)
		}
	}
}