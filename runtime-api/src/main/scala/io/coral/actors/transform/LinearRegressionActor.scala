package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import scala.concurrent.Future
import scalaz.OptionT
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{render, pretty}
import io.coral.actors.CoralActor

object LinearRegressionActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      intercept <- (json \ "attributes" \ "params" \ "intercept").extractOpt[Double]
      weights   <- (json \ "attributes" \ "params" \ "weights").extractOpt[Map[String, Double]]
    } yield(intercept, weights)
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[LinearRegressionActor], json))
  }
}

class LinearRegressionActor(json: JObject) extends CoralActor with ActorLogging {
  val (intercept, weights) = LinearRegressionActor.getParams(json).get

  var result: Double = _
  def jsonDef = json
  def state = Map.empty
  def timer = noTimer

  override def trigger = {
    json =>
      val inputVector = weights.keys.map(key => {
        (json \ key).extractOpt[Double] match {
          case Some(value) => value
          case None => throw new Exception ("Key does not exists")
        }}).toVector
      result = intercept + (inputVector zip weights.values).map(x => x._1 * x._2).sum
      Done
  }

  type TriggerResult = OptionT[Future, Unit]
  val Done:TriggerResult  = OptionT.some(Future.successful({}))
  val Error:TriggerResult = OptionT.none

  override def emit = {
    json: JObject =>
      val r = render("score" -> result) merge json
      r
  }

}
