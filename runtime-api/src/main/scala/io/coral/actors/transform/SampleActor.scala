package io.coral.actors.transform

import akka.actor.Props
import io.coral.actors.{SimpleEmitTrigger, CoralActor}
import io.coral.lib.Random
import org.json4s.JsonAST.JNothing
import org.json4s.{JObject, JValue}

import scala.concurrent.Future
import scalaz.OptionT

object SampleActor {

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      fraction <- (json \ "attributes" \ "params" \ "fraction").extractOpt[Double]
        .orElse((json \ "attributes" \ "params" \ "percentage").extractOpt[Double].map(p => 0.01 * p))
    } yield {
      fraction
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[SampleActor], json, Random))
  }

}

class SampleActor(json: JObject, random: Random)
  extends CoralActor(json)
  with SimpleEmitTrigger {

  val fraction: Double = SampleActor.getParams(json).get

  var randomStream: Stream[Boolean] = random.binomial(fraction)

  def next(): Boolean = {
    val value = randomStream.head
    randomStream = randomStream.tail
    value
  }

  override def simpleEmitTrigger(json: JObject): Option[JValue] = {
    next() match {
      case false => Some(JNothing)
      case true => Some(json)
    }
  }

}
