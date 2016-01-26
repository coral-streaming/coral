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

package io.coral.actors

import java.util.UUID
import akka.actor._
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import akka.util.Timeout
import com.github.t3hnar.bcrypt._
import com.typesafe.config.ConfigFactory
import io.coral.TestHelper
import io.coral.actors.RootActor.CreateHelperActors
import io.coral.actors.RuntimeAdminActor._
import io.coral.actors.database.CassandraActor
import io.coral.api.security.Authenticator.Invalidate
import io.coral.api.security.CoralAuthenticator
import io.coral.api.{Runtime, CoralConfig, DefaultModule}
import io.coral.utils.Utils
import org.json4s.JsonAST.JObject
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import org.json4s.native.JsonMethods._
import org.json4s._
import org.json4s.JsonDSL._
import TestHelper._

object RuntimeAdminActorSpec {
	def config(system: ActorSystem) = {
		val config = new CoralConfig(ConfigFactory.parseString(
			s"""coral.distributor.mode = "local"
			   | akka.actor.provider = "akka.actor.LocalActorRefProvider"
			   | coral.cluster.enable = false
			 """.stripMargin)
			.withFallback(system.settings.config))

		Utils.setPersistenceSystemProps(config)
		config
	}
}

@RunWith(classOf[JUnitRunner])
class RuntimeAdminActorSpec(_system: ActorSystem)
	extends TestKit(ActorSystem("RuntimeAdminActorSpec", RuntimeAdminActorSpec.config(_system).getConfig))
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with BeforeAndAfterEach {
	def this() = this(ActorSystem("coral"))
	implicit val injector = new DefaultModule(system.settings.config)
	implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
	implicit val timeout = Timeout(5.seconds)
	implicit val formats = org.json4s.DefaultFormats
	implicit var cassandra: TestActorRef[CassandraActor] = _
	var authenticator: ActorSelection = _
	var timestamp: Long = _
	implicit val config = RuntimeAdminActorSpec.config(_system)
	val salt = generateSalt
	val uniqueName1 = "ab12cd"
	val password1 = "pzzwrd"
	val uniqueName2 = "cd34ef"
	val password2 = "encrypted_password2"
	val userUUID1 = UUID.randomUUID()
	val userUUID2 = UUID.randomUUID()
	val runtimeUUID1 = UUID.randomUUID()
	val runtimeUUID2 = UUID.randomUUID()

	var adminActor: ActorSelection = _

	override def beforeAll() {
		cassandra = TestHelper.createCassandraActor(
			config.coral.cassandra.contactPoints.head.getHostName,
			config.coral.cassandra.port)
		TestHelper.prepareTables()

		val root = system.actorOf(Props(new RootActor()), "root")
		root ! CreateHelperActors()
		Thread.sleep(1000)

		adminActor = system.actorSelection("/user/root/admin")
		timestamp = System.currentTimeMillis()
		authenticator = system.actorSelection("/user/root/authenticator")

		insertUser(userUUID1, "Test user 1", "Department of business", "user1@ing.nl",
			uniqueName1, "+3162543556", password1.bcrypt(salt), timestamp, timestamp)
		insertUser(userUUID2, "Test user 2", "Department of business", "user2@ing.nl",
			uniqueName2, "+3162834453", password2.bcrypt(salt), timestamp, timestamp)

		authenticator ! Invalidate()
	}

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	override def beforeEach() {
		TestHelper.beforeEach()
	}

	class TestCrashException extends Exception("Test crash on purpose")

	class CrashActor extends RuntimeAdminActor() {
		val receiveException: Receive = {
			case ex: TestCrashException =>
				throw ex
		}

		override def receive = receiveException orElse super.receive
	}

	"A RuntimeAdminActor" must {
		"Succesfully create a runtime that creates output" in {
			val fileAndExpected = run(adminActor, userUUID1, 1, 1).head

			expectNoMsg(3.seconds)

			val actualOutput: List[JValue] = scala.io.Source.fromFile(fileAndExpected._1)
				.mkString.split("\n").map(line => parse(line)).toList

			val expectedOutput = List.fill[JValue](10)(
				parse(s"""{ "field1": "${fileAndExpected._2}" } """))

			assert(actualOutput == expectedOutput)
		}

		"Run multiple runtimes simultaneously" in {
			val nrRuntimesSimultaneously = 10
			val fileAndExpected: List[(String, String)] =
				run(adminActor, userUUID1, nrRuntimesSimultaneously, 2)

			expectNoMsg(2.second)

			fileAndExpected.foreach(tuple => {
				val actualOutput: List[JValue] = scala.io.Source.fromFile(tuple._1)
					.mkString.split("\n").map(line => parse(line)).toList

				val expectedOutput = List.fill[JValue](10)(
					parse(s"""{ "field1": "${tuple._2}" } """))
				assert(actualOutput == expectedOutput)
			})
		}

		"Crash the RuntimeAdminActor but continue the child actors" in {
			val jsonDef = parse(json(userUUID1, "runtime20")).asInstanceOf[JObject]
			val actual1 = Await.result(adminActor.ask(CreateRuntime(jsonDef, auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual1 \ "success").extract[Boolean] == true)

			val runtime1Actor = Await.result(adminActor.ask(GetRuntimeActor("ab12cd-runtime20")),
				timeout.duration).asInstanceOf[Option[ActorRef]]
			assert(runtime1Actor.isDefined)

			var terminated = false
			class WatchActor(probe: TestProbe) extends Actor {
				context.watch(runtime1Actor.get)
				def receive = {
					case message => terminated = true
				}
			}

			adminActor ! new TestCrashException()
			expectNoMsg(3.seconds)

			val probe = TestProbe()
			system.actorOf(Props(new WatchActor(probe)))
			assert(terminated == false)

			val stillRuntime1Actor: ActorSelection = system.actorSelection("/user/root/admin/ab12cd-runtime20")
			val stillRuntime1ActorRef: ActorRef = Await.result(stillRuntime1Actor.resolveOne, timeout.duration)
			assert(stillRuntime1ActorRef == runtime1Actor.get)

			val actual2 = Await.result(stillRuntime1ActorRef.ask(RuntimeActor.GetRuntimeInfo()),
				timeout.duration).asInstanceOf[JObject]
			val owner: UUID = UUID.fromString((actual2 \ "definition" \ "owner").extract[String])

			assert(TestHelper.validTimestamp(actual2 \ "startTime"))

			assert(owner == userUUID1)
		}

		"Create a second name for a runtime name that already exists" in {
			val jsonDef = parse(json("runtime30", userUUID1.toString, "/tmp/coral.log"))
				.asInstanceOf[JObject]
			val actual1 = Await.result(adminActor.ask(CreateRuntime(jsonDef, auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual1 \ "success").extract[Boolean] == true)
			assert((actual1 \ "definition" \ "name").extract[String] == "runtime30")

			expectNoMsg(1.second)

			val actual2 = Await.result(adminActor.ask(CreateRuntime(parse(json(
				"runtime31", userUUID1.toString, "/tmp/coral.log")).asInstanceOf[JObject], auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual2 \ "success").extract[Boolean] == true)
			assert((actual2 \ "definition" \ "name").extract[String] == "runtime31")

			expectNoMsg(1.second)

			val actual3 = Await.result(adminActor.ask(CreateRuntime(parse(json(
				"runtime32", userUUID2.toString, "/tmp/coral.log")).asInstanceOf[JObject], auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			// Different user, no problem
			assert((actual3 \ "success").extract[Boolean] == true)
			assert((actual3 \ "definition" \ "name").extract[String] == "runtime32")

			expectNoMsg(1.second)

			val actual4 = Await.result(adminActor.ask(CreateRuntime(parse(json(
				"runtime33", userUUID2.toString, "/tmp/coral.log")).asInstanceOf[JObject], auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual4 \ "success").extract[Boolean] == true)
			assert((actual4 \ "definition" \ "name").extract[String] == "runtime33")
		}

		"Create runtimes with different names immediately after each other for the same user" in {
			// This one will always succeed
			val actual1 = Await.result(adminActor.ask(CreateRuntime(parse(json(
				"runtime40", userUUID1.toString, "/tmp/coral.log")).asInstanceOf[JObject], auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual1 \ "success").extract[Boolean] == true)

			// No pause here on purpose

			val actual2 = Await.result(adminActor.ask(CreateRuntime(parse(json(
				"runtime50", userUUID1.toString, "/tmp/coral.log")).asInstanceOf[JObject], auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual2 \ "success").extract[Boolean])
		}

		// TODO: Make this work, for this CoralActor itself needs to be persistent
		/*
		"Process all messages even in case of a crash" in {
			// Start a runtime, crash the parent, wait X seconds,
			// look at output and still all messages should be processed
			// Success rate should be 100%

			val adminActor = system.actorOf(Props(new CrashActor()))
			val fileAndExpected = run(adminActor, 1).head

			adminActor ! new TestCrashException()

			expectNoMsg(5.seconds)

			val actualOutput: List[JValue] = scala.io.Source.fromFile(fileAndExpected._1)
				.mkString.split("\n").map(line => parse(line)).toList

			val expectedOutput = List.fill[JValue](10)(
				parse(s"""{ "field1": "${fileAndExpected._2}" } """))

			assert(actualOutput == expectedOutput)
		}
		*/

		"Recover from a crash" in {
			val randomString = java.util.UUID.randomUUID().toString.replace("-", "")
			val jsonString = json("runtime60", userUUID1.toString, "/tmp/coral.log", randomString)
			val jsonObj = parse(jsonString).asInstanceOf[JObject]

			Await.result(adminActor.ask(CreateRuntime(jsonObj, auth("coral"))), timeout.duration)

			val runtime1Actor = Await.result(adminActor.ask(GetRuntimeActor("ab12cd-runtime60")),
				timeout.duration).asInstanceOf[Option[ActorRef]]
			assert(runtime1Actor.isDefined)

			adminActor ! new TestCrashException()
			expectNoMsg(3.seconds)

			val runtimes = Await.result(adminActor.ask(GetAllRuntimeDefinitions()),
				timeout.duration).asInstanceOf[JObject]
			val actual = ((runtimes \ "runtimes").children(0) \ "definition").asInstanceOf[JObject]

			assert(actual == jsonObj)
		}

		"Create and delete runtimes on request" in {
			val file1 = "/tmp/coral1.log"
			val file2 = "/tmp/coral2.log"
			val name1 = "runtime70"
			val name2 = "runtime80"

			val json1 = parse(json(name1, userUUID1.toString, file1, "a")).asInstanceOf[JObject]
			val json2 = parse(json(name2, userUUID1.toString, file2, "a")).asInstanceOf[JObject]

			val actual1 = Await.result(adminActor.ask(CreateRuntime(json1, auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual1 \ "success").extract[Boolean] == true)

			expectNoMsg(500.millis)

			val actual2 = Await.result(adminActor.ask(CreateRuntime(json2, auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual2 \ "success").extract[Boolean] == true)

			expectNoMsg(1000.millis)

			val actual3 = Await.result(adminActor.ask(GetAllRuntimeDefinitions()),
				timeout.duration).asInstanceOf[JObject]

			expectNoMsg(500.millis)

			val actualRuntime1 = (actual3 \ "runtimes").children(0).asInstanceOf[JObject]
			val actualRuntime2 = (actual3 \ "runtimes").children(1).asInstanceOf[JObject]
			TestHelper.equalWithoutCreatedAndId(actualRuntime1, json1)
			TestHelper.equalWithoutCreatedAndId(actualRuntime2, json2)

			val adminPath = "akka.tcp://coral@127.0.0.1:2551/user/root/admin"
			val runtime1 = Runtime(runtimeUUID1, UUID.randomUUID(), name1,
				"ab12cd-" + name1, adminPath, 0, Some(UUID.randomUUID()), null, None, 0)
			val runtime2 = Runtime(runtimeUUID2, UUID.randomUUID(), name2,
				"ab12cd-" + name2, adminPath, 0, Some(UUID.randomUUID()), null, None, 0)

			val actual4 = Await.result(adminActor.ask(DeleteRuntime(runtime1)),
				timeout.duration).asInstanceOf[JObject]

			expectNoMsg(1000.millis)

			val expected4 =
				("action" -> "Delete runtime") ~
				("name" -> "ab12cd-runtime70") ~
				("success" -> true)

			assert(TestHelper.validTimestamp(actual4 \ "time"))
			val actualWithoutTimestamp = actual4.removeField(_._1 == "time")
			assert(actualWithoutTimestamp == expected4)

			expectNoMsg(500.millis)

			val actual5 = Await.result(adminActor.ask(GetAllRuntimeDefinitions()),
				timeout.duration).asInstanceOf[JObject]
			val expected5: JObject = parse(json("runtime80", userUUID1.toString,
				"/tmp/coral2.log", "a")).removeField(_._1 == "projectid").asInstanceOf[JObject]

			val actualWithoutProjectId = ((actual5 \ "runtimes").children(0) \ "definition")
				.asInstanceOf[JObject].removeField(_._1 == "projectid")

			assert(actualWithoutProjectId == expected5)

			val actual6 = Await.result(adminActor.ask(DeleteRuntime(runtime2)),
				timeout.duration).asInstanceOf[JObject]
			assert((actual6 \ "success").extract[Boolean] == true)

			expectNoMsg(1000.millis)

			val actual7 = Await.result(adminActor.ask(GetAllRuntimeDefinitions()),
				timeout.duration).asInstanceOf[JObject]
			val expected7 = parse(s"""{ "runtimes": [] }""")
			assert(actual7 == expected7)
		}

		"Delete all runtimes on request" in {
			val actual0 = Await.result(adminActor.ask(DeleteAllRuntimes()),
				timeout.duration).asInstanceOf[JObject]
			assert((actual0 \ "success").extract[Boolean] == true)

			expectNoMsg(500.millis)

			val name1 = "runtime90"
			val name2 = "runtime100"
			val file1 = "/tmp/coral1.log"
			val file2 = "/tmp/coral2.log"

			val actual1 = Await.result(adminActor.ask(CreateRuntime(
				parse(json(name1, userUUID1.toString, file1)).asInstanceOf[JObject], auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual1 \ "success").extract[Boolean] == true)

			expectNoMsg(500.millis)

			val actual2 = Await.result(adminActor.ask(CreateRuntime(
				parse(json(name2, userUUID1.toString, file2)).asInstanceOf[JObject], auth("coral"))),
				timeout.duration).asInstanceOf[JObject]
			assert((actual2 \ "success").extract[Boolean] == true)

			expectNoMsg(500.millis)

			val actual3 = Await.result(adminActor.ask(DeleteAllRuntimes()),
				timeout.duration).asInstanceOf[JObject]
			assert((actual3 \ "success").extract[Boolean] == true)

			expectNoMsg(500.millis)

			authenticator ! Invalidate()

			val actual4 = Await.result(adminActor.ask(GetAllRuntimeDefinitions()),
				timeout.duration).asInstanceOf[JObject]
			val expected4 = parse( s"""{ "runtimes": [] }""")
			assert(actual4 == expected4)
		}

		def runFrom1(adminActor: ActorSelection, ownerUUID: UUID, nrTimes: Int): List[(String, String)] = {
			run(adminActor, ownerUUID, nrTimes, 1)
		}

		def run(adminActor: ActorSelection, ownerUUID: UUID, nrTimes: Int, start: Int): List[(String, String)] = {
			Range.inclusive(start, nrTimes).map(i => {
				val randomString = UUID.randomUUID().toString.replace("-", "")
				val outputFile = "/tmp/coral" + i + ".log"
				val jsonDef = json("runtime" + i, ownerUUID.toString, outputFile, randomString)
				val jsonObj = parse(jsonDef).asInstanceOf[JObject]

				val runtime = Runtime(
					UUID.randomUUID(),
					UUID.randomUUID(),
					"",
					"ab12cd-runtime" + i,
					"local",
					0,
					Some(UUID.randomUUID()),
					JObject(),
					None,
					0)

				val actual1 = Await.result(adminActor.ask(CreateRuntime(jsonObj, auth("coral"))),
					timeout.duration).asInstanceOf[JObject]
				val expected1 = ("success" -> true) ~ ("definition" -> jsonObj)

				assert(TestHelper.equalWithoutCreatedAndId(actual1, expected1))

				val actual2 = Await.result(adminActor.ask(StartRuntime(runtime)),
					timeout.duration).asInstanceOf[JObject]
				assert((actual2 \ "success").extract[Boolean] == true)

				(outputFile, randomString)
			}).toList
		}
	}
}