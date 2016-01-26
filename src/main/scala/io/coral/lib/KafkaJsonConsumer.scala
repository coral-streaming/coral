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

import com.fasterxml.jackson.core.JsonParseException
import kafka.consumer._
import kafka.serializer.{Decoder, DefaultDecoder}
import org.json4s.JsonAST.{JNothing, JValue}
import org.json4s.jackson.JsonMethods._

object KafkaJsonConsumer {
	def apply() = new KafkaJsonConsumer(JsonDecoder)
	def apply(decoder: Decoder[JValue]) = new KafkaJsonConsumer(decoder)
}

class KafkaJsonConsumer(decoder: Decoder[JValue]) {
	def stream(topic: String, properties: Properties): KafkaJsonStream = {
		val connection = Consumer.create(new ConsumerConfig(properties))
		val stream = connection.createMessageStreamsByFilter(
			Whitelist(topic), 1, new DefaultDecoder, decoder)(0)
		new KafkaJsonStream(connection, stream)
	}
}

class KafkaJsonStream(connection: ConsumerConnector, stream: KafkaStream[Array[Byte], JValue]) {
	private lazy val it = stream.iterator

	// this method relies on a timeout value having been set
	@inline def hasNextInTime: Boolean =
		try {
			it.hasNext
		} catch {
			case cte: ConsumerTimeoutException => false
		}

	@inline def next: JValue = it.next.message
	@inline def commitOffsets = connection.commitOffsets
}

object JsonDecoder extends Decoder[JValue] {
	val encoding = "UTF8"

	override def fromBytes(bytes: Array[Byte]): JValue = {
		val s = new String(bytes, encoding)
		try {
			parse(s)
		} catch {
			case jpe: JsonParseException => JNothing
		}
	}
}