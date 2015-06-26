package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._

// coral
import io.coral.actors.CoralActor

object HttpBroadcastActor {
  implicit val formats = org.json4s.DefaultFormats

  def apply(json: JValue): Option[Props] = {
    Some(Props(classOf[HttpBroadcastActor], json))
  }
}

class HttpBroadcastActor(json: JObject) extends CoralActor with ActorLogging {
  def jsonDef = json
  def state   = Map.empty
  def trigger = defaultTrigger
  def emit    = emitPass
}