package io.coral.actors

import akka.actor.Props
import io.coral.actors.connector.{KafkaProducerActor, KafkaConsumerActor}
import io.coral.actors.database.CassandraActor
import io.coral.actors.transform._
import org.json4s._

class DefaultActorPropFactory extends ActorPropFactory {
  def getProps(actorType: String, params: JValue): Option[Props] = {
    actorType match {
      case "cassandra" => CassandraActor(params)
      case "fsm" => FsmActor(params)
      case "generator" => GeneratorActor(params)
      case "httpbroadcast" => HttpBroadcastActor(params)
      case "httpclient" => HttpClientActor(params)
      case "json" => JsonActor(params)
      case "kafka-consumer" => KafkaConsumerActor(params)
      case "kafka-producer" => KafkaProducerActor(params)
      case "lookup" => LookupActor(params)
      case "sample" => SampleActor(params)
      case "stats" => StatsActor(params)
      case "threshold" => ThresholdActor(params)
      case "window" => WindowActor(params)
      case "zscore" => ZscoreActor(params)
      case "linearregression" => LinearRegressionActor(params)
      case "markovscore" => MarkovScoreActor(params)
      case _ => None
    }
  }
}