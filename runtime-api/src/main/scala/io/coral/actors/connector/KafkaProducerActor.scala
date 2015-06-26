package io.coral.actors.connector

import java.util.Properties

import akka.actor.{Props, ActorLogging}
import io.coral.actors.CoralActor
import io.coral.lib.ConfigurationBuilder
import kafka.producer.{ProducerConfig, KeyedMessage, Producer}
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s.jackson.JsonMethods._

import scala.concurrent.Future
import scalaz.OptionT


object KafkaProducerActor {

  implicit val formats = org.json4s.DefaultFormats

  val builder = new ConfigurationBuilder("kafka.producer")

  def getParams(json: JValue) = {
    for {
      kafka <- (json \ "attributes" \ "params" \ "kafka").extractOpt[JObject]
      topic <- (json \ "attributes" \ "params" \ "topic").extractOpt[String]
    } yield {
      val properties = producerProperties(kafka)
      (properties, topic)
    }
  }

  private def producerProperties(json: JObject): Properties = {
    val properties = builder.properties
    json.values.foreach{ case(k: String, v: String) => properties.setProperty(k, v)}
    properties
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[KafkaProducerActor], json))
  }
}

class KafkaProducerActor(json: JObject) extends CoralActor with ActorLogging {
  val (properties, topic) = KafkaProducerActor.getParams(json).get

  val producer = createProducer(properties)

  def jsonDef = json

  def state = Map.empty

  def trigger = {
    json =>
      val key = (json \ "key").extractOpt[String]
      val message = (json \"message").extract[JObject]
      send(key, message)
      OptionT.some(Future.successful({}))
  }

  def createProducer(properties: Properties) = {
    new Producer[String, Array[Byte]](new ProducerConfig(properties))
  }

  def encode(message: JObject): Array[Byte] = {
    compact(message).getBytes("UTF-8")
  }

  private def send(key: Option[String], message: JObject) = {
    val encodedMessage = encode(message)
    val keyedMessage: KeyedMessage[String, Array[Byte]] = key match {
      case Some(key) => new KeyedMessage(topic, key, encodedMessage)
      case None => new KeyedMessage(topic, encodedMessage)
    }
    try {
      producer.send(keyedMessage)
    } catch {
      case e: Exception => log.error(e, "failed to send message to Kafka")
    }
  }

  def emit = emitNothing
}
