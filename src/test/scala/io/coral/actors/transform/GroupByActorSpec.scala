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

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import io.coral.actors.RuntimeActor
import io.coral.api.DefaultModule
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.languageFeature.postfixOps

class GroupByActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with ScalaFutures {
	def this() = this(ActorSystem("GroupByActorSpec"))
	implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
	implicit val injector = new DefaultModule(system.settings.config)
	val name = "runtime1"
	val userUUID1 = UUID.randomUUID()
	implicit val runtime = system.actorOf(Props(new RuntimeActor(name, userUUID1)), "coral")
	implicit val timeout = Timeout(100.millis)
	implicit val formats = org.json4s.DefaultFormats

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	// here is a dependency on the stats actor
	// in the current situation (the CoralActorFactory) it seems unavoidable
	// to depend in some tests on an existing actor instead of injecting a test actor
	def statsGroupBy: GroupByActor = {
		val createJson = parse(
			"""{ "type": "stats",
			  |  "params": { "field": "amount" },
			  |  "group": { "by": "tag" }
			  | }""".stripMargin
		).asInstanceOf[JObject]
		TestActorRef[GroupByActor](GroupByActor(createJson).get).underlyingActor
	}

	"A GroupByActor" should {
		/*
		"Extract the the create json" in {
			val createJson = parse(
				"""{ "type": "bla",
				  |  "bla": "bla bla",
				  |  "group": { "by": "some tag" },
				  |  "more": "bla bla bla"
				  | }""".stripMargin
			).asInstanceOf[JObject]
			val actor = TestActorRef[GroupByActor](GroupByActor(createJson).get).underlyingActor
			val expectedChildJson = parse(
				"""{ "type": "bla",
				  |  "bla": "bla bla",
				  |  "more": "bla bla bla"
				  | }""".stripMargin
			)
			actor.jsonDef should be(createJson)
			actor.jsonChildrenDef should be(expectedChildJson)
			actor.by should be("some tag")
		}

		"Initialize without children" in {
			val actor = statsGroupBy
			actor.children should be(Map.empty[String, Long])
			actor.state should be(Map(("actors", render(Map.empty[String, Long]))))
		}

		"Create a new child when triggered for non existing tag" in {
			val actor = statsGroupBy
			actor.children.size should be(0)
			actor.trigger(parse( """{ "tag": "a", "amount": 1.1 }""").asInstanceOf[JObject])
			awaitCond(actor.children.size == 1)
		}

		"Create no new child when triggered with existing group by field value" in {
			val actor = statsGroupBy
			actor.children.size should be(0)
			actor.trigger(parse( """{ "tag": "a", "amount": 1.1 }""").asInstanceOf[JObject])
			awaitCond(actor.children.size == 1)
			actor.trigger(parse( """{ "tag": "a", "amount": 2.2 }""").asInstanceOf[JObject])
			awaitCond(actor.children.size == 1)
		}

		"Create a new child when triggered with a new group by field value" in {
			val actor = statsGroupBy
			actor.children.size should be(0)
			actor.trigger(parse( """{ "tag": "a", "amount": 1.1 }""").asInstanceOf[JObject])
			awaitCond(actor.children.size == 1)
			actor.trigger(parse( """{ "tag": "b", "amount": 1.1 }""").asInstanceOf[JObject])
			awaitCond(actor.children.size == 2)
		}
		*/
	}
}