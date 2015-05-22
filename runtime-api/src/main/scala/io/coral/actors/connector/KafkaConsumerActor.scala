package io.coral.actors.connector

import java.util.Properties

import akka.actor.Props
import io.coral.actors.TemplateCoralActor
import io.coral.lib.ConfigurationBuilder
import kafka.consumer._
import kafka.message.MessageAndMetadata
import kafka.serializer.{StringDecoder, Decoder, DefaultDecoder}
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s.jackson.JsonMethods._

object KafkaConsumerActor {

  implicit val formats = org.json4s.DefaultFormats

  val consumerTimeout = 500 // ms

  val builder = new ConfigurationBuilder("kafka.consumer")

  def getParams(json: JValue) = {
    for {
      kafka <- (json \ "params" \ "kafka").extractOpt[JObject]
      topic <- (json \ "params" \ "topic").extractOpt[String]
    } yield {
      val properties = readKafkaConsumerProperties(kafka)
      val connection = consumer(properties, consumerTimeout)
      (connection, topic)
    }
  }

  def readKafkaConsumerProperties(json: JObject): Properties = {
    val properties = builder.properties
    json.values.foreach { case (k: String, v: String) =>
      properties.setProperty(k, v)
    }
    properties
  }

  def consumer(properties: Properties, timeout: Int) = {
    if (timeout > 0) properties.setProperty("consumer.timeout.ms", consumerTimeout.toString)
    Consumer.create(new ConsumerConfig(properties))
  }

  private object ReadMessageQueue

  class JsonDecoder extends Decoder[JObject] {

    val stringDecoder = new StringDecoder

    override def fromBytes(bytes: Array[Byte]): JObject = {
      val s = stringDecoder.fromBytes(bytes)
      parse(s).asInstanceOf[JObject]
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[KafkaConsumerActor], json, new JsonDecoder))
  }

}

class KafkaConsumerActor(json: JValue, decoder: Decoder[JObject]) extends TemplateCoralActor(json) {

  import KafkaConsumerActor.ReadMessageQueue

  val (connection, topic) = KafkaConsumerActor.getParams(json).get

  val size = 1

  lazy val stream: KafkaStream[Array[Byte], JObject] =
    connection.createMessageStreamsByFilter(Whitelist(topic), size, new DefaultDecoder, decoder)(0)

  lazy val it: ConsumerIterator[Array[Byte], JObject] = stream.iterator

  // this method relies on a timeout value having been set
  private def hasNextInTime = try {
    it.hasNext
  } catch {
    case cte: ConsumerTimeoutException => false
  }

  override def preStart(): Unit = {
    self ! ReadMessageQueue
  }

  override def receive = super.receive orElse receiveKafka

  def receiveKafka: Receive = {
    
    case ReadMessageQueue if hasNextInTime =>
      val mm: MessageAndMetadata[Array[Byte], JObject] = it.next
      transmit(mm.message())
      connection.commitOffsets
      self ! ReadMessageQueue

    case ReadMessageQueue =>
      self ! ReadMessageQueue
  }


}
