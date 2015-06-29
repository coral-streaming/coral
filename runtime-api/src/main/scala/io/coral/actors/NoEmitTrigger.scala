package io.coral.actors

import org.json4s.JsonAST.JObject
import org.json4s._

import scala.concurrent.Future

trait NoEmitTrigger extends Trigger {
  override def trigger: TriggerType =
    json => {
      noEmitTrigger(json)
      Future.successful(Some(JNothing))
    }

  def noEmitTrigger(json: JObject): Unit
}
