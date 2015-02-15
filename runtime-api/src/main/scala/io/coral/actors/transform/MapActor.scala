package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// coral
import io.coral.actors.CoralActor

object MapActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      lookup   <- (json \ "params" \ "lookup").extractOpt[Map[String, JObject]]
      key      <- (json \ "params" \ "key").extractOpt[String]
      function <- (json \ "params" \ "function").extractOpt[String]
    } yield {
      (key, lookup, function)
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[MapActor], json))
  }
}

/* RestActor sends requests to InListActor */
class MapActor(json: JObject) extends CoralActor with ActorLogging {
  def jsonDef = json
  val (key,lookup, function) = MapActor.getParams(json).get

  def state =  Map.empty

  //local state
  var lookupObject: Option[JValue] = None

  def trigger = {
    json: JObject =>
      for {
      // from trigger data
        value <- getTriggerInputField[String](json \ key)

      } yield {
        // compute (local variables & update state)
        lookupObject = lookup.get(value)
      }
  }

  def emit = {
    json: JObject =>

      //emit resulting json

      function match {
        case "enrich" => json merge render(lookupObject.getOrElse(JNothing))
        case "filter" => lookupObject map (_ => json) getOrElse(JNull)
        case "check"  => render(lookupObject.getOrElse(JNull))
      }
  }
}