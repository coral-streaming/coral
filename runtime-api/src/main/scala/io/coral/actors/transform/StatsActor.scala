package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}
import org.json4s.JsonAST.JValue

// json

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods.render

// coral
import io.coral.actors.{SimpleTimer, NoEmitTrigger, CoralActor}
import io.coral.lib.SummaryStatistics

import scala.language.implicitConversions

object StatsActor {

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      field <- (json \ "attributes" \ "params" \ "field").extractOpt[String]
    } yield {
      field
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[StatsActor], json))
    // todo: take better care of exceptions and error handling
  }
}

class StatsActor(json: JObject)
  extends CoralActor(json)
  with ActorLogging
  with NoEmitTrigger
  with SimpleTimer {

  implicit def double2jvalue(x: Double): JValue = if (x.isNaN) JNull else JDouble(x)

  val field = StatsActor.getParams(json).get

  val stats = SummaryStatistics.mutable

  override def state = Map(
    ("count", render(stats.count)),
    ("avg", render(stats.average)),
    ("sd", render(stats.populationSd)),
    ("min", render(stats.min)),
    ("max", render(stats.max))
  )

  override def simpleTimer = {
    stats.reset()
    Some(JNothing)
  }

  override def noEmitTrigger(json: JObject) = {
    for {
      value <- (json \ field).extractOpt[Double]
    } yield {
      stats.append(value)
    }
  }

}