package io.coral.actors

import akka.actor.Props
import org.json4s.JsonAST.JValue

trait ActorPropFactory {
  def getProps(actorType: String, params: JValue): Option[Props]
}