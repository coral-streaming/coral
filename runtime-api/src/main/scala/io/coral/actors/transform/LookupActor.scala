package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.jackson.JsonMethods.render

// coral
import io.coral.actors.CoralActor

object LookupActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      lookup   <- (json \ "attributes" \ "params" \ "lookup").extractOpt[Map[String, JObject]]
      key      <- (json \ "attributes" \ "params" \ "key").extractOpt[String]
      function <- (json \ "attributes" \ "params" \ "function").extractOpt[String]
    } yield {
      (key, lookup, function)
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[LookupActor], json))
  }
}

class LookupActor(json: JObject) extends CoralActor(json) with ActorLogging {
  val (key, lookup, function) = LookupActor.getParams(json).get

  //local state
  var lookupObject: Option[JValue] = None

  override def trigger = {
    json: JObject =>
      for {
      // from trigger data
        value <- getTriggerInputField[String](json \ key)
      } yield {
        // compute (local variables & update state)
        lookupObject = lookup.get(value)
      }
  }

  override def emit = {
    json: JObject =>
      function match {
        case "enrich" => json merge render(lookupObject.getOrElse(JNothing))
        case "filter" => lookupObject map (_ => json) getOrElse(JNull)
        case "check"  => render(lookupObject.getOrElse(JNull))
      }
  }
}