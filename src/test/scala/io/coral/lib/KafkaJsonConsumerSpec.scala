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

import kafka.consumer._
import kafka.message.MessageAndMetadata
import org.json4s.JsonAST.{JNothing, JValue}
import org.json4s.jackson.JsonMethods._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class KafkaJsonConsumerSpec extends WordSpec with Matchers with MockitoSugar {
	"KafkaJsonConsumer" should {
		"provide a stream" in {
			val consumer = KafkaJsonConsumer()
			intercept[IllegalArgumentException] {
        		consumer.stream("abc", new Properties())
      		}
		}
	}

	"KafkaJsonStream" should {
		val fakeConnection = mock[ConsumerConnector]
		doNothing.when(fakeConnection).commitOffsets

		val fakeMessage = mock[MessageAndMetadata[Array[Byte], JValue]]
		when(fakeMessage.key()).thenReturn("TestKey".getBytes)
		when(fakeMessage.message()).thenReturn(parse( """{ "json": "test" }"""))

		val fakeIterator = mock[ConsumerIterator[Array[Byte], JValue]]
		when(fakeIterator.hasNext()).thenReturn(true).thenReturn(false)
		when(fakeIterator.next()).thenReturn(fakeMessage)

		val fakeStream = mock[KafkaStream[Array[Byte], JValue]]
		when(fakeStream.iterator()).thenReturn(fakeIterator)

		"provide a next value" in {
			val kjs = new KafkaJsonStream(fakeConnection, fakeStream)
			kjs.hasNextInTime shouldBe true
			kjs.next shouldBe parse( """{ "json": "test" }""")
		}
	}

	"JsonDecoder" should {
		"convert bytes to Json object" in {
			val jsonString = """{ "hello": "json" }"""
			val bytes = jsonString.getBytes
			val jsonValue = parse(jsonString)
			JsonDecoder.fromBytes(bytes) shouldBe jsonValue
		}

		"return JNothing for invalid JSon" in {
			val jsonString = """hello"""
			val bytes = jsonString.getBytes
			JsonDecoder.fromBytes(bytes) shouldBe JNothing
		}
	}
}