package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._

// coral
import io.coral.actors.CoralActor

object HttpServerActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
    // from json actor definition
    // possible parameters server/client, url, etc
      t <- (json \ "type").extractOpt[String]
    } yield {
      t
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[HttpServerActor], json))
    // todo: take better care of exceptions and error handling
  }
}

class HttpServerActor(json: JObject) extends CoralActor with ActorLogging {
  def jsonDef = json
  def state = Map.empty
  def trigger = noProcess
  def emit = passThroughEmit
  def timer = notSet
}