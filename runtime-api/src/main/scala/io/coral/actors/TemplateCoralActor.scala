package io.coral.actors

import org.json4s.JValue

abstract class TemplateCoralActor(json: JValue) extends CoralActor {

  override def jsonDef: JValue = json

  override def timer: Timer = noTimer

  override def state: Map[String, JValue] = Map.empty[String, JValue]

  override def emit: Emit = emitNothing

  override def trigger: Trigger = defaultTrigger

}
