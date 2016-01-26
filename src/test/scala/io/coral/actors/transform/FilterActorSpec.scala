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
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.util.Timeout
import org.json4s.JObject
import org.json4s.JsonAST.JNothing
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import org.json4s.jackson.JsonMethods._

class FilterActorSpec(_system: ActorSystem) extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	implicit val timeout = Timeout(100.millis)
	def this() = this(ActorSystem("FilterActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"A FilterActor" should {
		"not create an actor when no filters are defined" in {
			val constructor = parse(
				""""type": "filter", "params": {}}""")
			val props = FilterActor(constructor)

			assert(props == None)
		}

		"not create an actor when one of the filters definitions is incorrect" in {
			val constructor = parse(
				"""{"type": "filter",
				  |"params": {"filters": [
				  |{"type": "startswith", "function": "exclude", "field": "key1", "param": "excluded"},
				  |{"type": "unknown", "function": "include", "field": "key2", "param": "included"}]}}""".stripMargin)
			val props = FilterActor(constructor)

			assert(props == None)
		}

		"create an actor" in {
			val constructor = parse(
				"""{ "type": "filter",
				  |"params": {"filters": [
				  |{"type": "startswith", "function": "exclude", "field": "key1", "param": "excluded"},
				  |{"type": "startswith", "function": "include", "field": "key2", "param": "included"}]}}""".stripMargin)
			val props = FilterActor(constructor)

			props.get.actorClass should be(classOf[FilterActor])
		}

		"emit when the filter is include and there is a match" in {
			val constructor = parse(
				"""{ "type": "filter",
				  |"params": {"filters": [
				  |{"type": "startswith", "function": "include", "field": "key", "param": "start"}]}}""".stripMargin)
			val props = FilterActor(constructor).get
			val filter = TestActorRef[FilterActor](props)

			val trigger = parse( """{"key": "starting"}""").asInstanceOf[JObject]
			val result = filter.underlyingActor.simpleEmitTrigger(trigger).get
			assert(result == trigger)
		}

		"not emit when the filter is include and there is no match" in {
			val constructor = parse(
				"""{ "type": "filter",
				  |"params": {"filters": [
				  |{"type": "startswith", "function": "include", "field": "key", "param": "start"}]}}""".stripMargin)
			val props = FilterActor(constructor).get
			val filter = TestActorRef[FilterActor](props)

			val trigger1 = parse( """{"key": "nostart"}""").asInstanceOf[JObject]
			val result1 = filter.underlyingActor.simpleEmitTrigger(trigger1).get
			assert(result1 == JNothing)

			val trigger2 = parse( """{"anotherkey": "starting"}""").asInstanceOf[JObject]
			val result2 = filter.underlyingActor.simpleEmitTrigger(trigger2).get
			assert(result2 == JNothing)
		}

		"emit when the filter is exclude and there is no match" in {
			val constructor = parse(
				"""{ "type": "filter",
				  |"params": {"filters": [
				  |{"type": "startswith", "function": "exclude", "field": "key", "param": "start"}]}}""".stripMargin)
			val props = FilterActor(constructor).get
			val filter = TestActorRef[FilterActor](props)

			val trigger1 = parse( """{"key": "nostart"}""").asInstanceOf[JObject]
			val result1 = filter.underlyingActor.simpleEmitTrigger(trigger1).get
			assert(result1 == trigger1)

			val trigger2 = parse( """{"anotherkey": "starting"}""").asInstanceOf[JObject]
			val result2 = filter.underlyingActor.simpleEmitTrigger(trigger2).get
			assert(result2 == trigger2)
		}

		"not emit when the filter is exclude and there is a match" in {
			val constructor = parse(
				"""{ "type": "filter",
				  |"params": {"filters": [
				  |{"type": "startswith", "function": "exclude", "field": "key", "param": "start"}]}}""".stripMargin)
			val props = FilterActor(constructor).get
			val filter = TestActorRef[FilterActor](props)

			val trigger = parse( """{"key": "starting"}""").asInstanceOf[JObject]
			val result = filter.underlyingActor.simpleEmitTrigger(trigger).get
			assert(result == JNothing)
		}

		"emit when all supplied filters are valid" in {
			val constructor = parse(
				"""{ "type": "filter",
				  |"params": {"filters": [
				  |{"type": "startswith", "function": "include", "field": "key1", "param": "start"},
				  |{"type": "startswith", "function": "include", "field": "key2", "param": "begin"}]}}""".stripMargin)
			val props = FilterActor(constructor).get
			val filter = TestActorRef[FilterActor](props)

			val trigger = parse( """{"key1": "starting", "key2": "beginning"}""").asInstanceOf[JObject]
			val result = filter.underlyingActor.simpleEmitTrigger(trigger).get
			assert(result == trigger)
		}

		"don't emit when one of the supplied filters is not valid" in {
			val constructor = parse(
				"""{ "type": "filter",
				  |"params": {"filters": [
				  |{"type": "startswith", "function": "include", "field": "key1", "param": "start"},
				  |{"type": "startswith", "function": "include", "field": "key2", "param": "begin"}]}}""".stripMargin)
			val props = FilterActor(constructor).get
			val filter = TestActorRef[FilterActor](props)

			val trigger = parse( """{"key1": "starting", "key2": "notbegin"}""").asInstanceOf[JObject]
			val result = filter.underlyingActor.simpleEmitTrigger(trigger).get
			assert(result == JNothing)
		}
	}
}