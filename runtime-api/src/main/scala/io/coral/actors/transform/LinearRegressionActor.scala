package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{render, pretty}
import io.coral.actors.{SimpleEmitTrigger, CoralActor}

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

class LinearRegressionActor(json: JObject)
  extends CoralActor(json)
  with ActorLogging
  with SimpleEmitTrigger {
  val (intercept, weights) = LinearRegressionActor.getParams(json).get

  override def simpleEmitTrigger(json: JObject): Option[JValue] = {
    val inputVector = weights.keys.map(key => {
      (json \ key).extractOpt[Double] match {
        case Some(value) => value
        case None => throw new Exception ("Key does not exists")
      }}).toVector
    val result = intercept + (inputVector zip weights.values).map(x => x._1 * x._2).sum
    Some(render("score" -> result) merge json)
  }

}
