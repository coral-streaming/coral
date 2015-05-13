package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import io.coral.actors.CoralActor
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s._

object JsonActor {

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      template <- (json \ "params" \ "template").extractOpt[JObject]
    } yield {
      template
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[JsonActor], json))
  }

}

class JsonActor (json: JObject) extends CoralActor {

  val template: JObject = JsonActor.getParams(json).get

  override def jsonDef: JValue = json

  override def timer: Timer = JNothing

  override def state: Map[String, JValue] = Map.empty[String, JValue]

  override def emit: Emit = ???

  override def trigger: Trigger = ???
}
