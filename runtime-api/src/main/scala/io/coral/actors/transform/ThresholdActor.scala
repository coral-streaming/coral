package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.render

// coral
import io.coral.actors.{SimpleEmitTrigger, CoralActor}

object ThresholdActor {
  implicit val formats = org.json4s.DefaultFormats
  
  def getParams(json: JValue) = {
    for {
      key <- (json \ "attributes" \ "params" \ "key").extractOpt[String]
      threshold <- (json \ "attributes" \ "params" \ "threshold").extractOpt[Double]
    } yield(key, threshold)
  }
  
  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[ThresholdActor], json))
    // todo: take better care of exceptions and error handling
  }
}

class ThresholdActor(json: JObject)
  extends CoralActor(json)
  with ActorLogging
  with SimpleEmitTrigger {

  val (key, threshold) = ThresholdActor.getParams(json).get
  
  override def simpleEmitTrigger(json: JObject): Option[JValue] = {
    for {
      value <- (json \ key).extractOpt[Double]
    } yield {
      val thresholdReached = value >= threshold
      thresholdReached match {
        case true => {
          val result = ("thresholdReached" -> key)
          json merge render(result)
        }
        case false => JNothing
      }
    }
  }

}