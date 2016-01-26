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

package io.coral.lib

import java.util.Properties

import io.coral.lib.KafkaJsonProducer.KafkaEncoder
import kafka.producer.{KeyedMessage, ProducerConfig, Producer}
import kafka.serializer.Encoder
import kafka.utils.VerifiableProperties
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s.jackson.JsonMethods._

object KafkaJsonProducer {
	type KafkaEncoder = Encoder[JValue]
	def apply() = new KafkaJsonProducer(classOf[JsonEncoder])
	def apply[T <: KafkaEncoder](encoder: Class[T]) = new KafkaJsonProducer(encoder)
}

class KafkaJsonProducer[T <: KafkaEncoder](encoderClass: Class[T]) {
	def createSender(topic: String, properties: Properties): KafkaSender = {
		val props = properties.clone.asInstanceOf[Properties]
		props.put("serializer.class", encoderClass.getName)
		val producer = createProducer(props)
		new KafkaSender(topic, producer)
	}

	def createProducer(props: Properties): Producer[String, JValue] = {
		new Producer[String, JValue](new ProducerConfig(props))
	}
}

class KafkaSender(topic: String, producer: Producer[String, JValue]) {
	def send(key: Option[String], message: JObject) = {
		val keyedMessage: KeyedMessage[String, JValue] = key match {
			case Some(key) => new KeyedMessage(topic, key, message)
			case None => new KeyedMessage(topic, message)
		}

		producer.send(keyedMessage)
	}
}

class JsonEncoder(verifiableProperties: VerifiableProperties) extends KafkaEncoder {
	override def toBytes(value: JValue): Array[Byte] = {
		compact(value).getBytes("UTF-8")
	}
}
