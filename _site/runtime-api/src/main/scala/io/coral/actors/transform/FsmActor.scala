package io.coral.actors.transform

// akka
import akka.actor.{Props, ActorLogging}

//json goodness
import org.json4s._
import org.json4s.jackson.JsonMethods._

// coral
import io.coral.actors.CoralActor


object FsmActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      key     <- (json \ "params" \ "key").extractOpt[String]
      table   <- (json \ "params" \ "table").extractOpt[Map[String, Map[String,String]]]
      s0      <- (json \ "params" \ "s0").extractOpt[String]
    } yield {
      (key, table, s0)
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[FsmActor], json))
  }
}

class FsmActor(json: JObject) extends CoralActor with ActorLogging {
  def jsonDef = json
  val (key, table, s0) = FsmActor.getParams(json).get

  // fsm state
  var s = s0

  def state =  Map(("s", JString(s)))

  def timer = notSet

  def trigger = {
    json: JObject =>
      for {
      // from trigger data
        value <- getTriggerInputField[String](json \ key)
      } yield {
        // compute (local variables & update state)
        val e = table.getOrElse(s, s0).asInstanceOf[Map[String, String]]
        s = e.getOrElse(value, s)
      }
  }

  def emit = doNotEmit
}