package io.coral.actors

import org.json4s._
import io.coral.actors.database.CassandraActor
import io.coral.actors.transform._
import akka.actor.Props

class DefaultActorPropFactory extends ActorPropFactory {
  def getProps(actorType: String, params: JValue): Option[Props] = {
    actorType match {
      case "fsm" => FsmActor(params)
      case "zscore" => ZscoreActor(params)
      case "stats" => StatsActor(params)
      case "lookup" => LookupActor(params)
      case "httpserver" => HttpServerActor(params)
      case "httpclient" => HttpClientActor(params)
      case "cassandra" => CassandraActor(params)
      //case "threshold" => ThresholdActor(params)
      case "window" => WindowActor(params)
      case _ => None
    }
  }
}