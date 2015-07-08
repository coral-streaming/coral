package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{render, pretty}
import io.coral.actors.{SimpleEmitTrigger, CoralActor}
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


class MarkovScoreActor(json: JObject)
  extends CoralActor(json)
  with ActorLogging
  with SimpleEmitTrigger {
  val transitionProbs = MarkovScoreActor.getParams(json).get

  override def simpleEmitTrigger(json: JObject): Option[JValue] = {

    for {
      clickPath <- (json \ "transitions").extractOpt[Array[String]]
    } yield {
      var score: Double = 1.0
      clickPath.sliding(2).foreach { case a =>
        transitionProbs.get(a(0), a(1)) match {
          case Some(prob) => score *= prob
          case _ => score = 0
        }
      }
      val result = ("score" -> score)
      val r = json merge render(result)
      println(pretty(r))
      r
    }
  }
}