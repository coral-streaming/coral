package io.coral.actors.connector

import java.util.Properties

import akka.actor.Props
import io.coral.actors.TemplateCoralActor
import org.json4s.JsonAST.{JObject, JValue}

object KafkaConsumerActor {

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      field <- (json \ "params" \ "kafka").extractOpt[JObject]
    } yield {
      field
    }
  }

  def readKafkaProperties(json: JObject): Properties = {
    val properties = new Properties()
    json.values.foreach { case (k: String, v: String) =>
      properties.setProperty(k, v)
    }
    properties
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[KafkaConsumerActor], json))
  }

}

class KafkaConsumerActor(json: JValue) extends TemplateCoralActor(json) {


}
