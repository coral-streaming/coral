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

import io.coral.actors.CoralActorFactory
import io.coral.api.DefaultModule
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit._
import akka.util.Timeout
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

@RunWith(classOf[JUnitRunner])
class ThresholdActorSpec(_system: ActorSystem) extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	implicit val timeout = Timeout(100.millis)
	def this() = this(ActorSystem("ThresholdActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"A ThresholdActor" must {
		val createJson = parse(
			"""{ "type": "threshold", "params": { "key": "key1", "threshold": 10.5 }}"""
				.stripMargin).asInstanceOf[JObject]

		implicit val injector = new DefaultModule(system.settings.config)

		// test invalid definition json as well !!!
		val props = CoralActorFactory.getProps(createJson).get
		val threshold = TestActorRef[ThresholdActor](props)

		// subscribe the testprobe for emitting
		val probe = TestProbe()
		threshold.underlyingActor.emitTargets += probe.ref

		"Emit when equal to the threshold" in {
			val json = parse( """{"key1": 10.5}""").asInstanceOf[JObject]
			threshold ! json
			probe.expectMsg(parse( """{ "key1": 10.5 }"""))
		}

		"Emit when higher than the threshold" in {
			val json = parse( """{"key1": 10.7}""").asInstanceOf[JObject]
			threshold ! json
			probe.expectMsg(parse( """{"key1": 10.7 }"""))
		}

		"Not emit when lower than the threshold" in {
			val json = parse( """{"key1": 10.4 }""").asInstanceOf[JObject]
			threshold ! json
			probe.expectNoMsg()
		}

		"Not emit when key is not present in triggering json" in {
			val json = parse( """{"key2": 10.7 }""").asInstanceOf[JObject]
			threshold ! json
			probe.expectNoMsg()
		}
	}
}