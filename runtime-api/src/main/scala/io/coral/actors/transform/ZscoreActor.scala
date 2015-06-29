package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

import scala.concurrent.Future

import scalaz._
import Scalaz._
import scalaz.OptionT._

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
      val result = for {
        subpath <- optionT(Future.successful((json \ by).extractOpt[String]))
        value <- optionT(Future.successful((json \ field).extractOpt[Double]))
        count <- optionT(getCollectInputField[Long]("stats", subpath, "count"))
        avg <- optionT(getCollectInputField[Double]("stats", subpath, "avg"))
        std <- optionT(getCollectInputField[Double]("stats", subpath, "sd"))
        outlier = isOutlier(count, avg, std, value)
      } yield(determineOutput(json, outlier))
      result.run
  }

  private def isOutlier(count: Long, avg: Double, std: Double, value: Double): Boolean = {
    val th = avg + score * std
    (value > th) & (count > 20)
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