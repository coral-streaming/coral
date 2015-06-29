package io.coral.actors.connector

import java.util.Properties

import akka.actor.{Props, ActorLogging}
import io.coral.actors.CoralActor
import io.coral.lib.KafkaJsonProducer.KafkaEncoder
import io.coral.lib.{KafkaJsonProducer, ConfigurationBuilder}
import org.json4s.JsonAST.{JObject, JValue}
import kafka.serializer.Encoder

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
    getParams(json).map(_ => Props(classOf[KafkaProducerActor[KafkaEncoder]], json, KafkaJsonProducer()))
  }

  def apply[T <: KafkaEncoder](json: JValue, encoder: Class[T]): Option[Props] = {
    getParams(json).map(_ => Props(classOf[KafkaProducerActor[T]], json, KafkaJsonProducer(encoder)))
  }
}

class KafkaProducerActor[T <: Encoder[JValue]](json: JObject, connection: KafkaJsonProducer[T]) extends CoralActor(json) with ActorLogging {
  val (properties, topic) = KafkaProducerActor.getParams(json).get

  lazy val kafkaSender = connection.createSender(topic, properties)

  override def trigger = {
    json =>
      val key = (json \ "key").extractOpt[String]
      val message = (json \"message").extract[JObject]
      send(key, message)
      OptionT.some(Future.successful({}))
  }

  private def send(key: Option[String], message: JObject) = {
    try {
      kafkaSender.send(key, message)
    } catch {
      case e: Exception => log.error(e, "failed to send message to Kafka")
    }
  }
}
