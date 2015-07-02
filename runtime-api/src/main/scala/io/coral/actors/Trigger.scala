package io.coral.actors

import org.json4s._

import scala.concurrent.Future
import org.json4s.JsonAST.{JObject, JValue}

trait Trigger {
  type TriggerType = JObject => Future[Option[JValue]]
  def trigger: TriggerType
}

trait NoEmitTrigger extends Trigger {
  override def trigger: TriggerType =
    json => {
      noEmitTrigger(json)
      Future.successful(Some(JNothing))
    }

  def noEmitTrigger(json: JObject): Unit
}

trait NoTrigger extends Trigger {
  override def trigger: TriggerType = json => Future.successful(Some(JNothing))
}

trait SimpleEmitTrigger extends Trigger {
  override def trigger: TriggerType = {
    json =>
      Future.successful(simpleEmitTrigger(json))
  }

  def simpleEmitTrigger(json: JObject): Option[JValue]
}



