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

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{TestProbe, ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.api.DefaultModule
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class MinMaxActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	implicit val timeout = Timeout(100.millis)
	implicit val formats = org.json4s.DefaultFormats
	implicit val injector = new DefaultModule(system.settings.config)
	def this() = this(ActorSystem("ZscoreActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"A MinMaxActor" must {
		val createJson = parse(
			"""{ "type": "minmax", "params": { "field": "field1", "min": 10.0, "max": 13.5 }}"""
				.stripMargin).asInstanceOf[JObject]

		implicit val injector = new DefaultModule(system.settings.config)

		val props = CoralActorFactory.getProps(createJson).get
		val threshold = TestActorRef[MinMaxActor](props)

		// subscribe the testprobe for emitting
		val probe = TestProbe()
		threshold.underlyingActor.emitTargets += probe.ref

		"Emit the minimum when lower than the min" in {
			val json = parse( """{"field1": 7 }""").asInstanceOf[JObject]
			threshold ! json
			probe.expectMsg(parse( """{ "field1": 10.0 }"""))
		}

		"Emit the maximum when higher than the max" in {
			val json = parse( """{"field1": 15.3 }""").asInstanceOf[JObject]
			threshold ! json
			probe.expectMsg(parse( """{"field1": 13.5 }"""))
		}

		"Emit the value itself when between the min and the max" in {
			val json = parse( """{"field1": 11.7 }""").asInstanceOf[JObject]
			threshold ! json
			probe.expectMsg(parse( """{"field1": 11.7 }"""))
		}

		"Emit object unchanged when key is not present in triggering json" in {
			val json = parse( """{"otherfield": 15.3 }""").asInstanceOf[JObject]
			threshold ! json
			probe.expectMsg(parse( """{"otherfield": 15.3 }"""))
		}
	}
}