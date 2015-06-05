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
    val stream = connection.createMessageStreamsByFilter(Whitelist(topic), 1, new DefaultDecoder, decoder)(0)
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

