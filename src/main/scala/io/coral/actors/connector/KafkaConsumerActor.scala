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

import akka.actor.Props
import io.coral.actors.CoralActor
import io.coral.actors.connector.KafkaConsumerActor.{StopReadingMessageQueue, ReadMessageQueue}
import io.coral.lib.{ConfigurationBuilder, KafkaJsonConsumer}
import kafka.serializer.Decoder
import kafka.tools.MessageFormatter
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.json4s.JsonAST.{JNothing, JObject, JValue}

object KafkaConsumerActor {
	case class ReadMessageQueue()
	case class StopReadingMessageQueue()

	implicit val formats = org.json4s.DefaultFormats
	val builder = new ConfigurationBuilder("kafka.consumer")

	def getParams(json: JValue) = {
		for {
			kafka <- (json \ "params" \ "kafka").extractOpt[JObject]
			topic <- (json \ "params" \ "topic").extractOpt[String]
		} yield {
			val properties = consumerProperties(kafka)
			(properties, topic)
		}
	}

	def consumerProperties(json: JObject): Properties = {
		val properties = builder.properties

		json.values.foreach {
			case (k: String, v: String) =>
				properties.setProperty(k, v)
		}

		properties
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[KafkaConsumerActor], json, KafkaJsonConsumer()))
	}

	def apply(json: JValue, decoder: Decoder[JValue]): Option[Props] = {
		getParams(json).map(_ => Props(classOf[KafkaConsumerActor], json, KafkaJsonConsumer(decoder)))
	}
}

class KafkaConsumerActor(json: JObject, connection: KafkaJsonConsumer) extends CoralActor(json) {
	val (properties, topic) = KafkaConsumerActor.getParams(json).get
	lazy val stream = connection.stream(topic, properties)
	var shouldStop = false

	override def preStart(): Unit = {
		super.preStart()
	}

	override def receiveExtra: Receive = {
		case ReadMessageQueue() if stream.hasNextInTime =>
			val message: JValue = stream.next
			stream.commitOffsets

			if (message != JNothing) {
				emit(message)
			}

			if (!shouldStop) {
				self ! ReadMessageQueue()
			}
		case ReadMessageQueue() =>
			self ! ReadMessageQueue()
		case StopReadingMessageQueue() =>
			shouldStop = true
	}

	/*
	def main (args: Array[String]) {
    val props = new Properties()
    props.put("groupid", "blabla")
    //props.put("socket.buffer.size", options.valueOf(socketBufferSizeOpt).toString)
    //props.put("fetch.size", options.valueOf(fetchSizeOpt).toString)
    props.put("auto.commit", "true")
    //props.put("autocommit.interval.ms", options.valueOf(autoCommitIntervalOpt).toString)
    //props.put("autooffset.reset", if(options.has(resetBeginningOpt)) "smallest" else "largest")
    props.put("zk.connect", "clrv0000002246.ic.ing.net:9092")
    val config = new ConsumerConfig(props)
    val skipMessageOnError = true

    val topic = "noliologs"

    val connector = Consumer.create(config)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        connector.shutdown()
        // if there is no group specified then avoid polluting zookeeper with persistent group data, this is a hack
        if(!options.has(groupIdOpt))
          tryCleanupZookeeper(options.valueOf(zkConnectOpt), options.valueOf(groupIdOpt))
      }
    })

    var stream: KafkaMessageStream = connector.createMessageStreams(Map(topic -> 1)).get(topic).get.get(0)
    val iter =
      if(maxMessages >= 0)
        stream.slice(0, maxMessages)
      else
        stream

    val formatter: MessageFormatter = messageFormatterClass.newInstance().asInstanceOf[MessageFormatter]
    formatter.init(formatterArgs)

    try {
      for(message <- iter) {
        try {
          formatter.writeTo(message, System.out)
        } catch {
          case e =>
            if (skipMessageOnError)
              logger.error("error processing message, skipping and resume consumption: " + e)
            else
              throw e
        }
      }
    } catch {
      case e => logger.error("error processing message, stop consuming: " + e)
    }

    System.out.flush()
    formatter.close()
    connector.shutdown()
  }
  */
}