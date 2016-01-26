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

import akka.actor.Props
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import io.coral.TestHelper
import io.coral.actors.RootActor
import io.coral.actors.RootActor.CreateHelperActors
import io.coral.api.security.AcceptAllAuthenticatorSpec
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import spray.http._

/**
 * This class does not test authorization. It assumes that
 * the right credentials are provided and that the user
 * is always authorized. It also does not test Cassandra connectivity,
 * which is also assumed to be present and working.
 * Authorization and Cassandra access are both tested in
 * the various AuthenticatorSpecs and CassandraActorSpec, respectively.
 */
@RunWith(classOf[JUnitRunner])
class ApiServiceSpec extends AcceptAllAuthenticatorSpec {
	override def createActorSystem = {
		val c = new CoralConfig(ConfigFactory.parseString(
			s"""coral.authentication.mode = "accept-all"
			   |coral.cluster.enable = false
			   |akka.actor.provider = "akka.actor.LocalActorRefProvider"
			 """.stripMargin)
			.withFallback(ConfigFactory.load()))
		initiateWithConfig(c)
	}

	override def beforeAll() {
		root = system.actorOf(Props(new RootActor()), "root")
		root ! CreateHelperActors()
		Thread.sleep(1000)

		cassandra = TestHelper.createCassandraActor(
			config.coral.cassandra.contactPoints.head.getHostName,
			config.coral.cassandra.port)
	}

	override def beforeEach() {
		TestHelper.beforeEach()
	}

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"The ApiService" when {
		"respond with the welcome message" in {
			Get("/").withHeaders(ContentTypeHeader, AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
				assert(responseAs[String] == "\"api is running. enjoy\"")
			}
		}

		"posting runtimes" should {
			"post a new runtime" in {
				runtime1NotDefined()
				val jsonDef1 = TestHelper.json("runtime1", acceptAllUUIDString)
				val id1 = postRuntime(jsonDef1, StatusCodes.Created)
				assert(id1.isDefined)
				Thread.sleep(500)
				// Runtime for user "coral" with name "runtime1",
				// id id1, definition jsonDef1 should be defined
				runtimeDefined("runtime1", jsonDef1, "coral", id1.get)
			}

			"do not accept a runtime with an empty definition" in {
				runtime1NotDefined()
				val invalidJson = "{}"
				val uuid = postRuntime(invalidJson, StatusCodes.BadRequest)
				assert(!uuid.isDefined)
				runtime1NotDefined()
			}

			"do not accept a runtime with an invalid definition" in {
				runtime1NotDefined()
				val invalidJson = TestHelper.json("", acceptAllUUIDString)
				val uuid = postRuntime(invalidJson, StatusCodes.BadRequest)
				assert(!uuid.isDefined)
				runtime1NotDefined()
			}

			"post and delete multiple runtimes" in {
				val name = "runtime10"
				runtimeNotDefined(name)
				val jsonDef1 = TestHelper.json(name, acceptAllUUIDString)
				val id1 = postRuntime(jsonDef1, StatusCodes.Created)
				waitAWhile()
				assert(id1.isDefined)
				runtimeDefined(name, jsonDef1, "coral", id1.get)
				deleteRuntime(name, StatusCodes.OK)
				waitAWhile()
				runtimeNotDefined(name)
				runtimeStatusPresent(cassandra, name, acceptAllUUID, _ == 4)

				val name2 = "runtime11"
				val jsonDef2 = TestHelper.json(name2, acceptAllUUIDString)
				val id2 = postRuntime(jsonDef2, StatusCodes.Created)
				waitAWhile()
				assert(id2.isDefined)
				runtimeDefined(name2, jsonDef2, "coral", id2.get)

				deleteAllRuntimes()
				waitAWhile()
				runtimeNotDefined(name)
				runtimeNotDefined(name2)
			}
		}

		"deleting runtimes" should {
			"delete a runtime" in {
				val name = "runtime30"
				runtimeNotDefined(name)
				val jsonDef1 = TestHelper.json(name, acceptAllUUIDString)
				postRuntime(jsonDef1, StatusCodes.Created)
				waitAWhile()
				deleteRuntime(name, StatusCodes.OK)
				waitAWhile()
				runtimeNotDefined(name)

				runtimeStatusPresent(cassandra, name, acceptAllUUID, _ == 4)
			}

			"return an error when deleting a nonexisting runtime" in {
				val name = "doesnotexist"
				runtimeNotDefined(name)
				deleteRuntime(name, StatusCodes.NotFound)
			}

			"delete all runtimes" in {
				val name1 = "runtime40"
				val name2 = "runtime41"
				runtimeNotDefined(name1)
				runtimeNotDefined(name2)
				val jsonDef1 = TestHelper.json(name1, acceptAllUUIDString)
				val jsonDef2 = TestHelper.json(name2, acceptAllUUIDString)
				postRuntime(jsonDef1, StatusCodes.Created)
				waitAWhile()
				postRuntime(jsonDef2, StatusCodes.Created)
				waitAWhile()
				deleteAllRuntimes()
				waitAWhile()
				runtimeNotDefined(name1)
				runtimeNotDefined(name2)
			}

			"Suggest a new name for a once deleted runtime" in {
				val name1 = "runtime50"
				runtimeNotDefined(name1)
				NoRuntimeMessagesInJournal(name1)
				val jsonDef1 = TestHelper.json(name1, acceptAllUUIDString)
				val uuid1 = postRuntime(jsonDef1, StatusCodes.Created)
				assert(uuid1.isDefined)
				waitAWhile()
				deleteRuntime(name1, StatusCodes.OK)
				waitAWhile()
				runtimeNotDefined(name1)

				runtimeStatusPresent(cassandra, name1, acceptAllUUID, _ == 4)

				val name2 = "runtime51"
				// The platform will rename runtime1 to runtime2 because runtime was previously
				// started and deleted, and keeping the name "runtime50" will replay all old eventsourced
				// messages. The runtime will be known under the address /api/runtimes/runtime51.
				val uuid2 = postRuntime(jsonDef1.replace(name1, name2), StatusCodes.Created)
				assert(uuid2.isDefined)
				runtimeNotDefined(name1)
				runtimeDefined(name2, jsonDef1.replace(name1, name2), "coral", uuid2.get)
			}
		}

		"accessing runtimes" should {
			"A runtime can be accessed by its UUID and by its name" in {
				val name = "runtime60"
				runtimeNotDefined(name)
				val jsonDef2 = TestHelper.json(name, userUUID1.toString)
				val uuid2 = postRuntime(jsonDef2, StatusCodes.Created)
				waitAWhile()
				assert(uuid2.isDefined)
				runtimeDefined(uuid2.get.toString, jsonDef2, uuid2.get)
				runtimeDefined(s"coral-$name", jsonDef2, uuid2.get)
				runtimeDefined(name, jsonDef2, "coral", uuid2.get)
			}
		}

		"starting and stopping runtimes" should {
			"start an existing runtime" in {
				val name = "runtime70"
				runtimeNotDefined(name)
				val jsonDef = TestHelper.json(name, userUUID1.toString)
				val uuid1 = postRuntime(jsonDef, StatusCodes.Created)
				waitAWhile()
				assert(uuid1.isDefined)
				startRuntime(name)
				waitAWhile()
				val status = getRuntimeStatus(name)
				assert(status == "running")
			}

			"do not start a nonexisting runtime" in {
				val name = "notthere"
				runtimeNotDefined(name)
				startRuntimeNotFound(name)
			}

			"stop an existing runtime" in {
				val name = "runtime80"
				runtimeNotDefined(name)
				val jsonDef = TestHelper.json(name, userUUID1.toString)
				postRuntime(jsonDef, StatusCodes.Created)
				waitAWhile()
				startRuntime(name)
				waitAWhile()
				val status1 = getRuntimeStatus(name)
				assert(status1 == "running")
				stopRuntime(name)
				waitAWhile()
				val status2 = getRuntimeStatus(name)
				assert(status2 == "stopped")
			}
		}
	}
}