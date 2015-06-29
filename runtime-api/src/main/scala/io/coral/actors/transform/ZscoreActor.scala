package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

import scala.concurrent.Future

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{render, compact}

// coral
import io.coral.actors.{SimpleEmitTrigger, CoralActor}


object ZscoreActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
    // from json actor definition
    // possible parameters server/client, url, etc
      by <- (json \ "attributes" \ "params" \ "by").extractOpt[String]
      field <- (json \ "attributes" \ "params" \ "field").extractOpt[String]
      score <- (json \ "attributes" \ "params" \ "score").extractOpt[Double]
    } yield {
      (by, field, score)
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[ZscoreActor], json))
    // todo: take better care of exceptions and error handling
  }

}

// metrics actor example
class ZscoreActor(json: JObject)
  extends CoralActor(json) {

  val (by, field, score) = ZscoreActor.getParams(jsonDef).get

  override def trigger = {
    json =>
      val triggerParams = getTriggerParams(json)

      triggerParams match {
        case None => Future.successful(None)
        case Some(params) => {
          val subpath = params._1
          val value = params._2
          val collectParams = getCollectParams(subpath)
          val outlier = collectParams.map{o => o.map{case (count, avg, std) => isOutlier(count, avg, std, value)}}
          outlier.map(o => o.map(determineOutput(json, _)))
        }
      }
  }

  private def isOutlier(count: Long, avg: Double, std: Double, value: Double): Boolean = {
    val th = avg + score * std
    (value > th) & (count > 20)
  }

  private def getCollectParams(subpath: String): Future[Option[(Long, Double, Double)]] = {
    val optionalParams = for {
      count <- getCollectInputField[Long]("stats", subpath, "count")
      avg <- getCollectInputField[Double]("stats", subpath, "avg")
      std <- getCollectInputField[Double]("stats", subpath, "sd")
    } yield ((count, avg, std))

    optionalParams.map{
      case ((optCount, optAvg, optStd)) => for {
        count <- optCount
          avg <- optAvg
        std <- optStd
      } yield ((count, avg, std))
    }
  }

  private def getTriggerParams(json: JObject): Option[(String, Double)] = {
    for {
      subpath <- (json \ by).extractOpt[String]
      value <- (json \ field).extractOpt[Double]
    } yield((subpath, value))
  }

  private def determineOutput(json: JObject, outlier: Boolean): JValue = {
      outlier match {
        case true =>
          // produce emit my results (dataflow)
          // need to define some json schema, maybe that would help
          val result = ("outlier" -> outlier)

          // what about merging with input data?
          val js = render(result) merge json

          //emit resulting json
          js

        case _ => JNothing
      }
  }
}