package io.coral.actors

import org.json4s.JValue

abstract class TemplateCoralActor(json: JValue) extends CoralActor {

  override def jsonDef: JValue = json
}
