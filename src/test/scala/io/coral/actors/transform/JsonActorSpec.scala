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

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class JsonActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	def this() = this(ActorSystem("JsonActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	implicit val timeout = Timeout(100.millis)
	def createJsonActor(json: JValue): JsonActor = {
		val props = JsonActor(json).get
		val actorRef = TestActorRef[JsonActor](props)
		actorRef.underlyingActor
	}

	"JsonActor" should {
		"have a standard coral props supplier" in {
			val json = parse("""{ "type": "json", "params": { "template": {} } }""")
			val props = JsonActor(json).get
			props.actorClass shouldBe classOf[JsonActor]
		}

		"read the template parameter" in {
			val template = """{ "a": "someReference" }"""
			val json = parse(s"""{ "type": "json", "params": { "template": $template } }""")
			val actor = createJsonActor(json)
			actor.template.template shouldBe parse(template)
		}

		"emit the json based on template" in {
			val templateJson =
				"""{ "a": "ALPHA",
				  |  "b": "${beta}",
				  |  "c": { "d": 123,
				  |         "e": "${epsilon}"
				  |       },
				  |  "f": 1,
				  |  "g": 1.0
				  |}""".stripMargin
			val json = parse(s"""{ "type": "json", "params": { "template": ${templateJson} } }""")
			val actor = createJsonActor(json)
			val triggerJson = parse(
				"""{ "beta": "xyz",
				  |  "epsilon": 987
				  |}""".stripMargin)
			val expectedJson = parse(
				"""{ "a": "ALPHA",
				  |  "c": { "d": 123,
				  |         "e": 987
				  |       },
				  |  "f": 1,
				  |  "b": "xyz",
				  |  "g": 1.0
				  |}""".stripMargin)
			actor.simpleEmitTrigger(triggerJson.asInstanceOf[JObject]) shouldBe Some(expectedJson)
		}
	}
}