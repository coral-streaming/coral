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
import akka.testkit._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class HttpBroadcastActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with ScalaFutures {
	def this() = this(ActorSystem("HttpBroadcastActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"A HttpBroadcastActor" should {
		"Instantiate with any json" in {
			val createJson = parse( """{ "type": "httpbroadcast" }""")
			val props = HttpBroadcastActor(createJson)
			assert(props.isDefined)
		}

		"Emit the trigger contents" in {
			val props = HttpBroadcastActor(parse( """{ "type": "httpbroadcast" }"""))
			val actor = TestActorRef[HttpBroadcastActor](props.get).underlyingActor
			val json = parse( """{"emit":"whatever"}""")
			val result = actor.simpleEmitTrigger(json.asInstanceOf[JObject])
			result should be(Some(json))
		}
	}
}
