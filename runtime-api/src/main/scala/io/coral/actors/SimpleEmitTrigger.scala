package io.coral.actors

import org.json4s.JsonAST.{JValue, JObject}

trait SimpleEmitTrigger {
  def simpleEmitTrigger(json: JObject): Option[JValue]
}
