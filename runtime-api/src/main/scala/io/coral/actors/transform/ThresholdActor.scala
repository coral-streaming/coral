package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.render

// coral
import io.coral.actors.CoralActor

object ThresholdActor {
  implicit val formats = org.json4s.DefaultFormats
  
  def getParams(json: JValue) = {
    for {
      key <- (json \ "params" \ "key").extractOpt[String]
      threshold <- (json \ "params" \ "threshold").extractOpt[Double]
    } yield(key, threshold)
  }
  
  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[ThresholdActor], json))
    // todo: take better care of exceptions and error handling
  }
}

class ThresholdActor(json: JObject) extends CoralActor with ActorLogging {
  val (key, threshold) = ThresholdActor.getParams(json).get
  
  var thresholdReached = false
  
  def jsonDef = json
  
  def state = Map.empty
  
  def timer = noTimer
  
  def trigger = {
    json =>
      for {
        value <- getTriggerInputField[Double](json \ key)
      } yield {
        thresholdReached = value >= threshold
      }
  }
  def emit = {
    json =>
      thresholdReached match {
        case true => {
          val result = ("thresholdReached" -> key)
          json merge render(result)
        }
        case false => JNothing
      }
  }

}