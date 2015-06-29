package io.coral.actors

import org.json4s.JsonAST.JObject

trait NoEmitTrigger {
  def noEmitTrigger(json: JObject): Unit
}
