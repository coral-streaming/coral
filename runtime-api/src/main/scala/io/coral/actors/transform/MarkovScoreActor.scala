package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{render, pretty}
import io.coral.actors.CoralActor
import scala.concurrent.Future
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.Future
import scalaz.OptionT
import scalaz.StreamT.Done

object MarkovScoreActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      jArray <- (json \ "attributes" \ "params" \ "transitionProbs").extractOpt[JArray]
      transitionProbs = parseJArray(jArray)
    } yield(transitionProbs)
  }

  def parseJArray(jArray: JArray) : Map[(String, String), Double] = {
    jArray.arr.map {element =>
      ((element \"source").extract[String], (element \"destination").extract[String]) -> (element \ "prob").extract[Double]
    }.toMap
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[MarkovScoreActor], json))
  }
}


class MarkovScoreActor(json: JObject) extends CoralActor with ActorLogging {
  val transitionProbs = MarkovScoreActor.getParams(json).get

  type TriggerResult = OptionT[Future, Unit]
  val Done:TriggerResult = OptionT.some(Future.successful({}))
  val Error:TriggerResult = OptionT.none
  var result: Double = 0.0

  def jsonDef = json
  def state = Map.empty
  def timer = noTimer

  var score: Double = 1.0

  override def trigger = {
    json =>
      for {
        clickPath <- getTriggerInputField[Array[String]](json \ "transitions")
      } yield {
          clickPath.sliding(2).foreach { case a =>
            transitionProbs.get(a(0), a(1)) match {
              case Some(prob) =>  score *= prob
              case _ => score = 0
            }
          }
          result = score
      }
  }

  override def emit = {
    json: JObject =>
      val r = render("score" -> result) merge json
      println(pretty(r))
      r
  }

}
