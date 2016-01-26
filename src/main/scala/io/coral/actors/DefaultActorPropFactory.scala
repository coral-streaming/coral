/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.actors

import akka.actor.Props
import io.coral.actors.connector.{LogActor, KafkaProducerActor, KafkaConsumerActor}
import io.coral.actors.database.CassandraActor
import io.coral.actors.transform._
import org.json4s._

class DefaultActorPropFactory extends ActorPropFactory {
  def getProps(actorType: String, params: JValue): Option[Props] = {
    actorType match {
      case "cassandra" => CassandraActor(params)
      case "filter" => FilterActor(params)
      case "fsm" => FsmActor(params)
      case "generator" => GeneratorActor(params)
      case "httpbroadcast" => HttpBroadcastActor(params)
      case "httpclient" => HttpClientActor(params)
      case "json" => JsonActor(params)
      case "kafka-consumer" => KafkaConsumerActor(params)
      case "kafka-producer" => KafkaProducerActor(params)
      case "log" => LogActor(params)
      case "lookup" => LookupActor(params)
      case "sample" => SampleActor(params)
      case "stats" => StatsActor(params)
      case "threshold" => ThresholdActor(params)
      case "window" => WindowActor(params)
      case "zscore" => ZscoreActor(params)
      case "linearregression" => LinearRegressionActor(params)
	  case "minmax" => MinMaxActor(params)
      case _ => None
    }
  }
}