package io.coral.actors.transform

import akka.actor.Props
import io.coral.actors.CoralActor
import io.coral.lib.Random
import org.json4s.JsonAST.JNothing
import org.json4s.{JObject, JValue}

import scala.concurrent.Future
import scalaz.OptionT

object SampleActor {

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      fraction <- (json \ "params" \ "fraction").extractOpt[Double]
        .orElse((json \ "params" \ "percentage").extractOpt[Double].map(p => 0.01 * p))
    } yield {
      fraction
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[SampleActor], json, Random))
  }

}

class SampleActor(json: JValue, random: Random) extends CoralActor {

  val fraction: Double = SampleActor.getParams(json).get

  var randomStream: Stream[Boolean] = random.binomial(fraction)

  def next(): Boolean = {
    val value = randomStream.head
    randomStream = randomStream.tail
    value
  }

  var pass: Boolean = false

  def jsonDef = json

  def timer = noTimer

  def state: Map[String, JValue] = Map.empty[String, JValue]

  def trigger = {
    _ => {
      pass = next()
      OptionT.some(Future.successful({}))
    }
  }

  def emit =
    json => pass match {
      case false => JNothing
      case true => json
    }

}
