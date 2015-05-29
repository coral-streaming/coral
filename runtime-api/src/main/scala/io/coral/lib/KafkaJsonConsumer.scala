package io.coral.lib

import java.util.Properties

import kafka.consumer._
import kafka.serializer.{Decoder, DefaultDecoder, StringDecoder}
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s.jackson.JsonMethods._

object KafkaJsonConsumer {

  val builder = new ConfigurationBuilder("kafka.consumer")

  def apply() = new KafkaJsonConsumer(JsonDecoder)

  def apply(decoder: Decoder[JValue]) = new KafkaJsonConsumer(decoder)

}

class KafkaJsonConsumer(decoder: Decoder[JValue]) {

  def connect(properties: Properties): ConsumerConnector =
    Consumer.create(new ConsumerConfig(properties))

  def stream(topic: String, properties: Properties): KafkaJsonStream = {
    val connection = connect(properties)
    val stream = connection.createMessageStreamsByFilter(Whitelist(topic), 1, new DefaultDecoder, decoder)(0)
    new KafkaJsonStream(connection, stream)
  }

}

class KafkaJsonStream(connection: ConsumerConnector, stream: KafkaStream[Array[Byte], JValue]) {

  lazy val it = stream.iterator

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

  val stringDecoder = new StringDecoder

  override def fromBytes(bytes: Array[Byte]): JObject = {
    val s = stringDecoder.fromBytes(bytes)
    parse(s).asInstanceOf[JObject]
  }

}

