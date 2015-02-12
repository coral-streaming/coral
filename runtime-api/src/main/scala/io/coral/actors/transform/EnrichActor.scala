package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// coral
import io.coral.actors.CoralActor

object EnrichActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      lookup <- (json \ "params" \ "lookup").extractOpt[Map[String, JObject]]
      field <- (json \ "params" \ "field").extractOpt[String]
    } yield {
      (field, lookup)
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[EnrichActor], json))
  }
}

/* RestActor sends requests to InListActor */
class EnrichActor(json: JObject) extends CoralActor with ActorLogging {
  def jsonDef = json
  val (field,lookup) = EnrichActor.getParams(json).get

  def state =  Map.empty

  //local state
  var lookupObject: JValue = JNothing

  def trigger = {
    json: JObject =>
      for {
      // from trigger data
        value <- getTriggerInputField[String](json \ field)

      } yield {
        // compute (local variables & update state)
        lookupObject = lookup.getOrElse(value, JNothing)
      }
  }

  def emit = {
    json: JObject =>

      // what about merging with input data?
      val js = render(lookupObject) merge json

      //emit resulting json
      js
  }
}