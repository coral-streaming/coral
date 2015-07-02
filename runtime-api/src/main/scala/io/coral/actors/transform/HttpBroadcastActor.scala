package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._

// coral
import io.coral.actors.{SimpleEmitTrigger, CoralActor}

object HttpBroadcastActor {
  implicit val formats = org.json4s.DefaultFormats

  def apply(json: JValue): Option[Props] = {
    Some(Props(classOf[HttpBroadcastActor], json))
  }
}

class HttpBroadcastActor(json: JObject)
  extends CoralActor(json)
  with ActorLogging
  with SimpleEmitTrigger {

  override def simpleEmitTrigger(json: JObject): Option[JValue] = {
    Some(json)
  }
}