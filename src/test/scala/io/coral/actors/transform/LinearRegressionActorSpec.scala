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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import io.coral.actors.CoralActorFactory
import io.coral.api.DefaultModule
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.util.Timeout
import org.json4s.native.Serialization.write
import scala.concurrent.duration._

class LinearRegressionActorSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	def this() = this(ActorSystem("LinearRegressionActorSpec"))

	implicit val timeout = Timeout(100.millis)
	implicit val injector = new DefaultModule(system.settings.config)

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	def createLinearRegressionActor(intercept: Double, weights: Map[String, Double]) = {
		implicit val formats = DefaultFormats
		val str =
			s"""{ "type":"linearregression",
			   |"params": { "intercept": $intercept,
			   |"weights": ${write(weights)}
			   |}}""".stripMargin

		val createJson = parse(str).asInstanceOf[JObject]
		val props = CoralActorFactory.getProps(createJson).get
		val actorTestRef = TestActorRef[LinearRegressionActor](props)

		val probe = TestProbe()
		actorTestRef.underlyingActor.emitTargets += probe.ref
		(actorTestRef, probe)
	}

	"LinearRegressionActor" should {
		"Instantiate from companion object" in {
			val (actor, _) = createLinearRegressionActor(0, Map("salary" -> 2000))
			actor.underlyingActor.intercept should be(0)
			actor.underlyingActor.weights should be(Map("salary" -> 2000))
		}

		"process trigger data when all the features are available even with different order" in {
			val (actor, probe) = createLinearRegressionActor(0, Map("age" -> 0.2, "salary" -> 0.1))
			val message = parse( s"""{"salary": 4000, "age": 40}""").asInstanceOf[JObject]
			actor ! message

			probe.expectMsg(parse( s"""{"score": 408.0, "salary": 4000, "age": 40}"""))
		}

		"emit when score is calculated" in {
			val (actor, probe) = createLinearRegressionActor(0, Map("salary" -> 10))
			val message = parse( s"""{"salary": 2000}""").asInstanceOf[JObject]
			actor ! message

			probe.expectMsg(parse( s"""{"score": 20000.0, "salary": 2000}"""))
		}

		"not emit when keys are missing" in {
			val (actor, probe) = createLinearRegressionActor(0, Map("age" -> 0.2, "salary" -> 10))
			val message = parse( s"""{"salary": 2000}""").asInstanceOf[JObject]
			actor ! message

			probe.expectNoMsg
		}
	}
}