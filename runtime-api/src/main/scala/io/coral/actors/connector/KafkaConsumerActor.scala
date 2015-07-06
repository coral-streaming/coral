package io.coral.actors.connector

import java.util.Properties

import akka.actor.Props
import io.coral.actors.CoralActor
import io.coral.lib.{ConfigurationBuilder, KafkaJsonConsumer}
import kafka.serializer.Decoder
import org.json4s.JsonAST.{JNothing, JObject, JValue}

object KafkaConsumerActor {

  implicit val formats = org.json4s.DefaultFormats

  val builder = new ConfigurationBuilder("kafka.consumer")

  def getParams(json: JValue) = {
    for {
      kafka <- (json \ "attributes" \ "params" \ "kafka").extractOpt[JObject]
      topic <- (json \ "attributes" \ "params" \ "topic").extractOpt[String]
    } yield {
      val properties = consumerProperties(kafka)
      (properties, topic)
    }
  }

  def consumerProperties(json: JObject): Properties = {
    val properties = builder.properties
    json.values.foreach { case (k: String, v: String) => properties.setProperty(k, v) }
    properties
  }

  object ReadMessageQueue

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[KafkaConsumerActor], json, KafkaJsonConsumer()))
  }

  def apply(json: JValue, decoder: Decoder[JValue]): Option[Props] = {
    getParams(json).map(_ => Props(classOf[KafkaConsumerActor], json, KafkaJsonConsumer(decoder)))
  }

}

class KafkaConsumerActor(json: JObject, connection: KafkaJsonConsumer) extends CoralActor(json) {

  import KafkaConsumerActor.ReadMessageQueue

  val (properties, topic) = KafkaConsumerActor.getParams(json).get

  lazy val stream = connection.stream(topic, properties)

  override def preStart(): Unit = {
    super.preStart()
    self ! ReadMessageQueue
  }

  override def receiveExtra: Receive = {

    case ReadMessageQueue if stream.hasNextInTime =>
      val message: JValue = stream.next
      stream.commitOffsets
      if (message != JNothing) {
        emit(message)
      }
      self ! ReadMessageQueue

    case ReadMessageQueue =>
      self ! ReadMessageQueue
  }


}
