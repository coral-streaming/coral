package io.coral.actors

import org.json4s.JObject

import scala.concurrent.Future
import org.json4s.JsonAST.JValue

trait Trigger {
  type TriggerType = JObject => Future[Option[JValue]]
  def trigger: TriggerType
}
