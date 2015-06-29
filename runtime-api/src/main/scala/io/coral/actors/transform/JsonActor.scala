package io.coral.actors.transform

import akka.actor.Props
import io.coral.actors.{SimpleEmitTrigger, CoralActor}
import io.coral.lib.JsonTemplate
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s._

object JsonActor {

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      template <- (json \ "attributes" \ "params" \ "template").extractOpt[JObject]
      if (JsonTemplate.validate(template))
    } yield {
      template
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[JsonActor], json))
  }

}

class JsonActor(json: JObject)
  extends CoralActor(json)
  with SimpleEmitTrigger {

  val template = JsonTemplate(JsonActor.getParams(json).get)

  override def simpleEmitTrigger(json: JObject): Option[JValue] = {
    Some(template.interpret(json))
  }

}
