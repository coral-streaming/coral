package io.coral.actors.transform

import io.coral.actors.CoralActor
import akka.actor.ActorLogging
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scalaz.OptionT
import scala.concurrent.Future
import akka.actor.Props

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
  private val (key, threshold) = ThresholdActor.getParams(json).get
  
  private var thresholdReached = false
  
  def jsonDef = json
  
  def state = Map.empty
  
  def timer = notSet
  
  override def trigger = {
    json: JObject =>
    thresholdReached = false
    for {
      value <- getTriggerInputField[Double](json \ key)
    } yield {
      thresholdReached = value >= threshold
    }
  }
  
  override def emit = {
    json: JObject =>
    thresholdReached match {
      case true => {
        val result = ("thresholdReached" -> key)
        json merge render(result)
      }
      case false => JNothing
    }
  }
}