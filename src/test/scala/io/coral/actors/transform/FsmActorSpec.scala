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

package io.coral.actors.transform

import akka.actor.{ActorInitializationException, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps

class FsmActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with ScalaFutures {

	def this() = this(ActorSystem("FsmActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	implicit val timeout = Timeout(100 millis)
	implicit val formats = org.json4s.DefaultFormats

	def createFsmActor(json: JValue): FsmActor = {
		val jsonObject = json.asInstanceOf[JObject]
		val props = Props(new FsmActor(jsonObject))
		val actorRef = TestActorRef[FsmActor](props)
		actorRef.underlyingActor
	}

	def createTestFsmActor = {
		val json = parse(
			s"""{
			   |"type": "fsm",
			   |"params": {
			   | "key": "transactionsize",
			   | "table": {
			   |   "normal": {
			   |     "small": "normal",
			   |     "large": "normal",
			   |     "x-large": "suspicious"
			   |   },
			   |   "suspicious": {
			   |     "small": "normal",
			   |     "large": "suspicious",
			   |     "x-large": "alarm",
			   |     "oeps": "unknown"
			   |   },
			   |   "alarm":{
			   |     "small": "suspicious",
			   |     "large": "alarm",
			   |     "x-large": "alarm"
			   |   }
			   | },
			   | "s0": "normal"
			   |} }""".stripMargin)
		createFsmActor(json)
	}

	def trigger(fsm: FsmActor, key: String) = {
		val json = parse( s"""{ "transactionsize": "${key}" }""").asInstanceOf[JObject]
		fsm.noEmitTrigger(json)
	}

	"An FsmActor" should {

		"Instantiate with complete json" in {
			val json = parse(
				"""{
				  |  "type": "fsm",
				  |  "params": {
				  |    "key": "a",
				  |    "table": {"aa": {"bb":"cc"}},
				  |    "s0": "aa" } }""".stripMargin)
			val fsm = createFsmActor(json)
			fsm.key should be("a")
			fsm.table should be(Map("aa" -> Map("bb" -> "cc")))
			fsm.s0 should be("aa")
		}

		"Instantiate from companion object" in {
			val json = parse(
				"""{
				  |  "type": "fsm",
				  |  "params": {
				  |    "key": "a",
				  |    "table": {"aa": {"bb":"cc"}},
				  |    "s0": "aa" } }""".stripMargin)
			val props = FsmActor(json)
			val fsm = TestActorRef[FsmActor](props.get).underlyingActor
			fsm.key should be("a")
			fsm.table should be(Map("aa" -> Map("bb" -> "cc")))
			fsm.s0 should be("aa")
		}

		"Not instantiate with a json without key/table/s0" in {
			val json = parse(
				"""{
				  |  "type": "fsm",
				  |  "params": {
				  |    "key": "a",
				  |    "table": {"aa": {"bb":"cc"}},
				  |    "s0": "does not exist in able" } }""".stripMargin)
			val props = FsmActor(json)
			intercept[ActorInitializationException] {
        new FsmActor(json.asInstanceOf[JObject])
      }
		}

		"Not instantiate with a json with invalid s0" in {
			val json = parse( """{ "test": "whatever" }""")
			intercept[ActorInitializationException] {
        new FsmActor(json.asInstanceOf[JObject])
      }
		}

		"Have a state initialized to s0" in {
			val fsm = createTestFsmActor
			fsm.s should be("normal")
			fsm.state should be(Map("s" -> JString("normal")))
		}

		"Have change state on trigger (happy flow)" in {
			val fsm = createTestFsmActor
			fsm.state should be(Map("s" -> JString("normal")))
			trigger(fsm, "small")
			fsm.state should be(Map("s" -> JString("normal")))

			trigger(fsm, "x-large")
			fsm.state should be(Map("s" -> JString("suspicious")))

			trigger(fsm, "x-large")
			fsm.state should be(Map("s" -> JString("alarm")))

			trigger(fsm, "large")
			fsm.state should be(Map("s" -> JString("alarm")))

			trigger(fsm, "small")
			fsm.state should be(Map("s" -> JString("suspicious")))

			trigger(fsm, "small")
			fsm.state should be(Map("s" -> JString("normal")))
		}

		"Keep current state after unknown or empty value" in {
			val fsm = createTestFsmActor
			fsm.state should be(Map("s" -> JString("normal")))
			trigger(fsm, "x-large")
			fsm.state should be(Map("s" -> JString("suspicious")))

			// now FSM is in a non-initial state
			trigger(fsm, "doesnotexist")
			fsm.state should be(Map("s" -> JString("suspicious")))

			fsm.trigger(parse("{}").asInstanceOf[JObject])
			fsm.state should be(Map("s" -> JString("suspicious")))
		}

		"Revert to initial state when an unknown state is provided in as transition result" in {
			val fsm = createTestFsmActor
			fsm.state should be(Map("s" -> JString("normal")))
			trigger(fsm, "x-large")
			fsm.state should be(Map("s" -> JString("suspicious")))

			// now FSM is in a non-initial state
			trigger(fsm, "oeps")
			fsm.state should be(Map("s" -> JString("unknown")))

			trigger(fsm, "large")
			fsm.state should be(Map("s" -> JString("normal")))
		}

	}

}
