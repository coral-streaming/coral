package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import io.coral.actors.CoralActor
import io.coral.lib.SummaryStatistics
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods.render

object StatsActor {

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      field <- (json \ "params" \ "field").extractOpt[String]
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

  def renderDouble(x: Double) = if (!x.isNaN) render(x) else render(JNull)

  def jsonDef = json

  val field = StatsActor.getParams(json).get

  val stats = SummaryStatistics.mutable

  def state = Map(
    ("count", render(stats.count)),
    ("avg", renderDouble(stats.average)),
    ("sd", renderDouble(stats.populationSd)),
    ("min", renderDouble(stats.min)),
    ("max", renderDouble(stats.max))
  )

  def timer = {
    stats.reset()
    JNothing
  }

  def trigger = {
    json: JObject =>
      for {
        value <- getTriggerInputField[Double](json \ field)
      } yield {
        stats.append(value)
      }
  }

  def emit = doNotEmit
}