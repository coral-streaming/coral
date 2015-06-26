package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}
import org.json4s.JsonAST.JValue

// json

import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods.render

// coral
import io.coral.actors.CoralActor
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

class StatsActor(json: JObject) extends CoralActor with ActorLogging {

  implicit def double2jvalue(x: Double): JValue = if (x.isNaN) JNull else JDouble(x)

  def jsonDef = json

  val field = StatsActor.getParams(json).get

  val stats = SummaryStatistics.mutable

  def state = Map(
    ("count", render(stats.count)),
    ("avg", render(stats.average)),
    ("sd", render(stats.populationSd)),
    ("min", render(stats.min)),
    ("max", render(stats.max))
  )

  override def timer = {
    stats.reset()
    JNothing
  }

  override def trigger = {
    json: JObject =>
      for {
        value <- getTriggerInputField[Double](json \ field)
      } yield {
        stats.append(value)
      }
  }

  def emit = emitNothing
}