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

import akka.actor.{Props, ActorLogging}
import io.coral.actors.{NoEmitTrigger, CoralActor}
import io.coral.lib.KafkaJsonProducer.KafkaEncoder
import io.coral.lib.{KafkaJsonProducer, ConfigurationBuilder}
import org.json4s.JsonAST.{JObject, JValue}
import kafka.serializer.Encoder

object KafkaProducerActor {
	implicit val formats = org.json4s.DefaultFormats
	val builder = new ConfigurationBuilder("kafka.producer")

	def getParams(json: JValue) = {
		for {
			kafka <- (json \ "params" \ "kafka").extractOpt[JObject]
			topic <- (json \ "params" \ "topic").extractOpt[String]
		} yield {
			val properties = producerProperties(kafka)
			(properties, topic)
		}
	}

	private def producerProperties(json: JObject): Properties = {
		val properties = builder.properties
		json.values.foreach { case (k: String, v: String) => properties.setProperty(k, v) }
		properties
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[KafkaProducerActor[KafkaEncoder]], json, KafkaJsonProducer()))
	}

	def apply[T <: KafkaEncoder](json: JValue, encoder: Class[T]): Option[Props] = {
		getParams(json).map(_ => Props(classOf[KafkaProducerActor[T]], json, KafkaJsonProducer(encoder)))
	}
}

class KafkaProducerActor[T <: Encoder[JValue]](json: JObject, connection: KafkaJsonProducer[T])
	extends CoralActor(json)
	with NoEmitTrigger
	with ActorLogging {
	val (properties, topic) = KafkaProducerActor.getParams(json).get
	lazy val kafkaSender = connection.createSender(topic, properties)

	override def noEmitTrigger(json: JObject) = {
		val key = (json \ "key").extractOpt[String]
		val message = (json \ "message").extract[JObject]
		send(key, message)
	}

	private def send(key: Option[String], message: JObject) = {
		try {
			kafkaSender.send(key, message)
		} catch {
			case e: Exception => log.error(e, "failed to send message to Kafka")
		}
	}
}
