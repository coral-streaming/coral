package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import scala.concurrent.Future
import scalaz.OptionT
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{render, pretty}

//import org.apache.spark.SparkContext
//import org.apache.spark.mllib.optimization.{L1Updater, SimpleUpdater, SquaredL2Updater}
//import org.apache.spark.mllib.regression.LinearRegressionWithSGD
//import org.apache.spark.mllib.util.MLUtils

// coral
import io.coral.actors.CoralActor

object LinearRegressionActor {
  implicit val formats = org.json4s.DefaultFormats
  //val model = new LinearRegressionWithSGD()

  def getParams(json: JValue) = {
    for {
      intercept <- (json \ "params" \ "intercept").extractOpt[Double]
      weights   <- (json \ "params" \ "weights").extractOpt[Map[String, Double]]
    } yield(intercept, weights)
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[LinearRegressionActor], json))
    //model.intercept = getParams(json).get._1
    //model.weights = getParams(json).get._2.values.toVector
  }
}

class LinearRegressionActor(json: JObject) extends CoralActor with ActorLogging {
  val (intercept, weights) = LinearRegressionActor.getParams(json).get

  var result: Double = _
  def jsonDef = json
  def state = Map.empty
  def timer = noTimer

//  def vectorNormalization(inputVector: Vector[Double]): Vector[Double] = {
//    inputVector.map(e => e/inputVector.sum )
//  }

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



  // there is a bug in this method: when features are less than weights, its still working as it finds the tuple of feature and weight
//  override def trigger = {
//    json =>json
//     val inputVector = weights.map( e => ((json \ e._1).extractOpt[Double], e._2) ).toList
//
//     //check all features are there
//     val allValid = inputVector.exists( _._1.isDefined)
//
//      if (allValid) {
//        result = inputVector.map(e => e._1.getOrElse(0d) * e._2).sum + intercept
//        Done
//      } else {
//        Error
//      }
//  }

  override def emit = {
    json: JObject =>
      val r = render("score" -> result) merge json
      print(pretty(r))
      r
  }

}
