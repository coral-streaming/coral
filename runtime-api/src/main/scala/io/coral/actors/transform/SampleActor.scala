package io.coral.actors.transform

import akka.actor.Props
import org.json4s.JsonAST.JValue

object SampleActor {

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      fraction <- (json \ "params" \ "fraction").extractOpt[Double]
        .orElse(
          for {
            percentage <- (json \ "params" \ "percentage").extractOpt[Double]
          } yield {
            percentage / 100.0
          })
    } yield {
      fraction
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[SampleActor], json))
  }

}

class SampleActor(json: JValue) {

}
