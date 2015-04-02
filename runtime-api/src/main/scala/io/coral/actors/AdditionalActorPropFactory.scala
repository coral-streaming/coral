package io.coral.actors

import org.json4s._
import io.coral.actors.database.CassandraActor
import io.coral.actors.transform._
import akka.actor.Props

class AdditionalActorPropFactory extends ActorPropFactory {
  def getProps(actorType: String, params: JValue): Option[Props] = {
    actorType match {
      case "threshold" => ThresholdActor(params)
      case _  => None
    }
  }
}