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

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import io.coral.actors.CoralActor._
import org.json4s.JsonAST.JValue
import org.junit.runner.RunWith
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.collection.immutable.SortedSet
import scala.concurrent.Await
import scala.language.postfixOps
import scala.util.Success
import akka.util.Timeout
import org.json4s.JObject
import akka.actor._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.collection.immutable.Map
import scala.concurrent.Future
import akka.pattern.ask
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class CoralActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with ScalaFutures {
	def this() = this(ActorSystem("coral"))

	implicit val timeout = Timeout(100 millis)
	implicit val formats = org.json4s.DefaultFormats
	val root = TestActorRef[CoralActor](Props(new MinimalCoralActor), "coral")

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	class MinimalCoralActor extends CoralActor(JObject()) {

	}

	def createCoralActor(props: Props = null, name: String = ""): CoralActor = {
		val _props = if (props != null) props else Props(new MinimalCoralActor)
		val ref =
			if (name == "") TestActorRef[CoralActor](_props)
			else TestActorRef[CoralActor](_props, root, name)
		ref.underlyingActor
	}

	"A CoralActor" should {
		"Have a 'jsonDef' method that returns the json definition" in {
			val testJson = parse( """{ "test": "jsonDef" }""").asInstanceOf[JObject]
			class TestCoralActor extends CoralActor(testJson) {

			}
			val coral = createCoralActor(Props(new TestCoralActor()))
			coral.jsonDef should be(testJson)
		}

		"Have an 'askActor' method to ask another actor by name" in {
			val coral = createCoralActor()
			val probe = TestProbe()
			val result = coral.askActor(probe.ref.path.toString, "ask")
			probe.expectMsg("ask")
			probe.reply("ask:response")
			assert(result.isCompleted && result.value == Some(Success("ask:response")))
		}

		"Have a 'tellActor' method to tell another by name" in {
			val coral = createCoralActor()
			val probe = TestProbe()
			coral.tellActor(probe.ref.path.toString, "tell")
			probe.expectMsg("tell")
		}

		"Have an 'in' method" in {
			val coral = createCoralActor()
			val probe = TestProbe()
			coral.in(10 millis) {
				coral.tellActor(probe.ref.path.toString, "msg2")
			}
			probe.expectMsg(100 millis, "msg2")
		}

		"Handle any JSON message" in {
			val testJson: JValue = parse( """{ "test": "emit" }""")
			class TestCoralActor extends MinimalCoralActor {
				override def trigger = json => Future.successful(Some(testJson.merge(json)))
			}
			val coral = createCoralActor(Props(new TestCoralActor))
			val probe = TestProbe()
			val json = parse( """{ "something": "else" }""")
			val expected = testJson.merge(json)
			coral.emitTargets += probe.ref
			coral.self ! json
			probe.expectMsg(expected)
		}

		"Ignore an incomplete JSON message (that is, makes trigger returns nothing)" in {
			val testJson: JValue = parse( """{ "test": "incomplete" }""")
			class TestCoralActor extends MinimalCoralActor {
				override def trigger = _ => Future.successful(None)
			}
			val coral = createCoralActor(Props(new TestCoralActor))
			val probe = TestProbe()
			coral.emitTargets += probe.ref
			coral.self ! testJson
			probe.expectNoMsg(100 millis)
		}

		"Ignore an JSON message that makes trigger fail" in {
			val testJson: JValue = parse( """{ "test": "fail" }""")
			class TestCoralActor extends MinimalCoralActor {
				override def trigger = _ => Future.failed({
					new Exception("Test exception on purpose")
				})
			}

			val coral = createCoralActor(Props(new TestCoralActor))
			val probe = TestProbe()
			coral.emitTargets += probe.ref
			coral.self ! testJson
			probe.expectNoMsg(100 millis)
		}

		"Handle a 'Shunt' message" in {
			val testJson: JValue = parse( """{ "test": "emit" }""")
			class TestCoralActor extends MinimalCoralActor {
				override def trigger = json => Future.successful(Some(testJson.merge(json)))
			}
			val coral = createCoralActor(Props(new TestCoralActor))
			val json = parse( """{ "something": "else" }""")
			val expected = testJson.merge(json)
			coral.self ! Shunt(json.asInstanceOf[JObject])
			expectMsg(expected)
		}

		"Ignore a 'Shunt' message that triggers none" in {
			val testJson: JValue = parse( """{ "test": "emit" }""")
			class TestCoralActor extends MinimalCoralActor {
				override def trigger = _ => Future.successful(None)
			}
			val coral = createCoralActor(Props(new TestCoralActor))
			val json = parse( """{ "something": "else" }""")
			coral.self ! Shunt(json.asInstanceOf[JObject])
			expectNoMsg(100 millis)
		}

		"Have 'noProcess' produce empty future option" in {
			val coral = createCoralActor()
			val result = coral.trigger(parse( """{"test": "whatever"}""").asInstanceOf[JObject])
			whenReady(result) {
				value => value should be(Some(JNothing))
			}
		}

		"Be activated after a 'Trigger' message" in {
			val testJson: JValue = parse( """{ "test": "trigger" }""")
			class TestCoralActor extends MinimalCoralActor {
				var wasExecuted = false

				override def trigger = _ => Future.successful({
					wasExecuted = true
					None
				})
			}

			val coral = createCoralActor(Props(new TestCoralActor))

			coral.process(parse("{}").asInstanceOf[JObject])
			expectNoMsg(100 millis)
			coral.asInstanceOf[TestCoralActor].wasExecuted should be(true)
		}

		"Be defined in concrete implementations of 'trigger'" in {
			val testJson: JValue = parse( """{ "test": "trigger2" }""")
			class TestCoralActor extends MinimalCoralActor {
				var wasExecuted = false

				override def trigger = _ => Future.successful {
					wasExecuted = true
					None
				}
			}
			val coral = createCoralActor(Props(new TestCoralActor))
			val result = coral.trigger(testJson.asInstanceOf[JObject])
			whenReady(result) {
				value => value should be(None)
			}
			coral.asInstanceOf[TestCoralActor].wasExecuted should be(true)
		}

		"Emit to actors registered with a 'RegisterActor' message" in {
			val coral = createCoralActor()
			val probe = TestProbe()
			coral.self ! RegisterActor(probe.ref)
			coral.emitTargets should be(SortedSet(probe.ref))
		}

		"Have a 'emit' method" in {
			val coral = createCoralActor()
			val probe1 = TestProbe()
			val probe2 = TestProbe()
			coral.emitTargets += probe2.ref
			coral.emitTargets += probe1.ref
			val json = parse( """{ "test": "transmit" }""")
			coral.emit(json)
			probe1.expectMsg(json)
			probe2.expectMsg(json)
			coral.emit(JNothing)
			probe1.expectNoMsg(100 millis)
		}

		"Have a default implementation of no state" in {
			val coral = createCoralActor(Props(new MinimalCoralActor))
			coral.state should be(Map.empty)
		}

		"Be defined in concrete implementations of 'state'" in {
			val testState = Map("key" -> JDouble(1.6))
			class TestCoralActor extends MinimalCoralActor {
				override def state: Map[String, JValue] = testState
			}
			val coral = createCoralActor(Props(new TestCoralActor))
			coral.state should be(testState)
		}

		"Be accessible with a 'GetField' message" in {
			val testValue = JDouble(3.1)
			val testState = Map("key" -> testValue)
			val expected: JObject = ("key" -> testValue)
			class TestCoralActor extends MinimalCoralActor {
				override def state: Map[String, JValue] = testState
			}

			val coral = createCoralActor(Props(new TestCoralActor))
			coral.self ! GetField("key")
			expectMsg(expected)
			coral.self ! GetField("non-existing key")
			expectMsg(JNothing)
		}
	}
}