package io.coral.actors

import org.json4s.JsonAST.{JValue, JObject}

import scala.concurrent.Future

trait SimpleEmitTrigger extends Trigger {
  override def trigger: TriggerType = {
    json =>
      Future.successful(simpleEmitTrigger(json))
  }

  def simpleEmitTrigger(json: JObject): Option[JValue]
}
