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

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import io.coral.lib.{NotSoRandom, Random}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps

class SampleActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with ScalaFutures {
	def this() = this(ActorSystem("SampleActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	def arbitrarySampleActor(): SampleActor = {
		val json = parse(
			"""{ "type": "sample",
			  | "params": { "fraction": 0.707 } }
			""".stripMargin)
		val props = SampleActor(json).get
		TestActorRef[SampleActor](props).underlyingActor
	}

	def notSoRandomSampleActor(fraction: Double, randoms: Double*): SampleActor = {
		val json = parse(
			s"""{ "type": "sample", "params": { "fraction": ${fraction} } }
     		 """.stripMargin)
		val source = NotSoRandom(randoms: _*)
		val props = Props(classOf[SampleActor], json, Random(source))
		TestActorRef[SampleActor](props).underlyingActor
	}

	implicit val timeout = Timeout(100 millis)

	"A SampleActor" should {

		"Be instantiated with sample fraction" in {
			val json = parse("""{ "type": "sample", "params": { "fraction": 0.5 }}""".stripMargin)
			val props = SampleActor(json).get
			props.actorClass() should be(classOf[SampleActor])
			val actor = TestActorRef[SampleActor](props).underlyingActor
			actor.fraction should be(0.5)
		}

		"Not be instantiated without fraction or percentage" in {
			val json = parse("""{ "type": "sample", "params": { "bla": "blabla" }}""".stripMargin)
			SampleActor(json) should be(None)
		}

		"Be constructible with a io.coral.lib.Random for random boolean stream" in {
			val actor = notSoRandomSampleActor(fraction = 0.5, randoms = 0.1, 0.49, 0.50, 0.51, 0.8, 0.4)
			actor.next() should be(true)
			actor.next() should be(true)
			actor.next() should be(false)
			actor.next() should be(false)
			actor.next() should be(false)
			actor.next() should be(true)
		}

		"Should trigger true or false according to random binomial sequence" in {
			val actor = notSoRandomSampleActor(fraction = 0.7, randoms = 0.8, 0.6)
			val json = parse( """{ "something": "whatever" }""").asInstanceOf[JObject]

			val result1 = actor.simpleEmitTrigger(json)
			result1 should be(Some(JNothing))

			val result2 = actor.simpleEmitTrigger(json)
			result2 should be(Some(json))
		}

		"Should have trigger and emit cooperate" in {
			val actor = notSoRandomSampleActor(fraction = 0.7, randoms = 0.6, 0.8)
			val ref = actor.self
			val json = parse( """{ "something": "whatever" }""").asInstanceOf[JObject]
			val probe = TestProbe()
			actor.emitTargets += probe.ref
			ref ! json
			probe.expectMsg(json)
			ref ! json
			probe.expectNoMsg(100 millis)
		}
	}
}