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

package io.coral.actors.connector

import java.util.Properties

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import io.coral.lib.{KafkaSender, JsonEncoder, KafkaJsonProducer}
import io.coral.lib.KafkaJsonProducer.KafkaEncoder
import kafka.producer.Producer
import org.json4s.JsonAST.{JValue, JObject}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike, Matchers}

class KafkaProducerActorSpec(_system: ActorSystem) extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	def this() = this(ActorSystem("KafkaProducerActorSpec"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"A KafkaProducerActor" should {
		"not create create an actor when the definition is incorrect" in {
			val constructor = parse("""{
			      | "type": "kafka-producer",
				  | "params" : {
				  |   "topic": "test"
				  | }
				  |}""".stripMargin).asInstanceOf[JObject]
			val props = KafkaProducerActor(constructor)
			assert(props == None)
		}

		"create an actor" in {
			val constructor = parse("""{
				  | "type": "kafka-producer",
				  | "params": {
				  |   "topic": "test",
				  |   "kafka": {}
				  | }
				  |}""".stripMargin)
			val props = KafkaProducerActor(constructor)
			props.get.actorClass should be(classOf[KafkaProducerActor[KafkaEncoder]])
		}

		"use the provided parameters and the configured parameters" in {
			val constructor = parse("""{
			      | "type": "kafka-producer",
				  | "params" : {
				  |   "topic": "test",
				  |   "kafka": {
				  |     "metadata.broker.list": "host1:80,host2:81"
				  |   }
				  | }
				  |}""".stripMargin).asInstanceOf[JObject]

			val props = KafkaProducerActor(constructor).get
			val actorRef = TestActorRef[KafkaProducerActor[KafkaEncoder]](props)
			assert(actorRef.underlyingActor.topic == "test")
			val properties = actorRef.underlyingActor.properties
			assert(properties.getProperty("metadata.broker.list") == "host1:80,host2:81")

			// Property read from application.conf
			assert(properties.getProperty("producer.type") == "async")
		}

		"send the JSON provided to Kafka" in {
			val constructor = parse("""{
			      | "type": "kafka-producer",
				  | "params" : {
				  |   "topic": "test",
				  |   "kafka": {}
				  |  }
				  |}""".stripMargin).asInstanceOf[JObject]
			val messageJson = """{"key3": "value3", "key4": "value4"}"""
			val keyJson = s""""key": "${key}", """
			val triggerJson = parse( s"""{${keyJson}"message": ${messageJson}}""").asInstanceOf[JObject]

			val producer = new MyKafkaJsonProducer
			val props = Props(classOf[KafkaProducerActor[KafkaEncoder]], constructor, producer)
			val actorRef = TestActorRef[KafkaProducerActor[KafkaEncoder]](props)
			actorRef.underlyingActor.trigger(triggerJson)

			assert(producer.sender.receivedKey == Some("key"))
			assert(producer.sender.receivedMessage == parse(messageJson))
		}
	}
}

class MyKafkaJsonProducer extends KafkaJsonProducer(classOf[JsonEncoder]) {
	var sender: MyKafkaSender = _

	override def createSender(topic: String, properties: Properties): KafkaSender = {
		val producer = mock(classOf[Producer[String, JValue]])
		sender = new MyKafkaSender(topic, producer)
		sender
	}
}

class MyKafkaSender(topic: String, producer: Producer[String, JValue]) extends KafkaSender(topic, producer) {
	var receivedKey: Option[String] = _
	var receivedMessage: JObject = _

	override def send(key: Option[String], message: JObject) = {
		receivedKey = key
		receivedMessage = message
	}
}
