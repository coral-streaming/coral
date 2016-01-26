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

import io.coral.actors.connector.{LogActor, KafkaProducerActor, KafkaConsumerActor}
import io.coral.actors.database.CassandraActor
import io.coral.actors.transform._
import io.coral.lib.KafkaJsonProducer.KafkaEncoder
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpecLike}
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class DefaultActorPropFactorySpec
	extends WordSpecLike
	with Matchers {
	"The DefaultActorPropFactory" should {
		val factory = new DefaultActorPropFactory

		"Provide nothing for unknown type" in {
			val props = factory.getProps("nonexisting", parse( """{}"""))
			props should be(None)
		}

		"Provide a CassandraActor for type 'cassandra'" in {
			val json =
				"""{
				  |"type": "cassandra",
				  |"params": {
				  |"seeds": ["0.0.0.0"], "keyspace": "test"
				  |}}""".stripMargin
			val props = factory.getProps("cassandra", parse(json))
			props.get.actorClass should be(classOf[CassandraActor])
		}

		"Provide a FilterActor for type 'filter'" in {
			val json =
				"""{
				  |"type": "filter",
				  |"params": {
				  |   "filters": [
				  |     {"type": "startswith", "function": "include", "field": "key", "param": "start"}
				  |   ]
				  |}}""".stripMargin

			val props = factory.getProps("filter", parse(json))
			props.get.actorClass should be(classOf[FilterActor])
		}

		"Provide an FsmActor for type 'fsm'" in {
			val json =
				"""{
				  |"type": "fsm",
				  |"params": {
				  |   "key": "transactionsize",
				  |   "table": {
				  |     "normal": {
				  |       "small": "normal"
				  |     }
				  |   },
				  |   "s0": "normal"
				  |}}""".stripMargin
			val props = factory.getProps("fsm", parse(json))
			props.get.actorClass should be(classOf[FsmActor])
		}

		"Provide a GeneratorActor for type 'generator'" in {
			val json =
				"""{
				  |"type": "generator",
				  |"params": {
				  |"format": {  }
				  |"timer": { "rate": 1 }
				  |}}""".stripMargin
			val props = factory.getProps("generator", parse(json))
			props.get.actorClass should be(classOf[GeneratorActor])
		}

		"Provide a HttpBroadcastActor for type 'httpbroadcast'" in {
			val json = """{ "type": "httpbroadcast" }""".stripMargin
			val props = factory.getProps("httpbroadcast", parse(json))
			props.get.actorClass should be(classOf[HttpBroadcastActor])
		}

		"Provide a HttpClientActor for type 'httpclient'" in {
			val json =
				"""{ "type": "httpclient",
				  |"params": {"method": "GET", "url": "http://localhost", "mode": "static", "response": "none" }}""".stripMargin
			val props = factory.getProps("httpclient", parse(json))
			props.get.actorClass should be(classOf[HttpClientActor])
		}

		"Provide a SampleActor for type 'sample'" in {
			val json =
				"""{ "type": "sample",
				  |"params": { "fraction": 0.1010010001 }
				  |}""".stripMargin
			val props = factory.getProps("sample", parse(json))
			props.get.actorClass should be(classOf[SampleActor])
		}

		"Provide a JsonActor for type 'json'" in {
			val json =
				"""{ "type": "json",
				  |"params": {"template": {"a": "${b}"}}
				  |}""".stripMargin
			val props = factory.getProps("json", parse(json))
			props.get.actorClass should be(classOf[JsonActor])
		}

		"Provide a KafkaConsumerActor for type 'kafka-consumer'" in {
			val json =
				"""{ "type": "actors",
				  |"type": "kafka-consumer",
				  |"params": { "topic": "bla", "kafka": {} }
				  |}""".stripMargin
			val props = factory.getProps("kafka-consumer", parse(json))
			props.get.actorClass should be(classOf[KafkaConsumerActor])
		}

		"Provide a KafkaProducerActor for type 'kafka-producer'" in {
			val json =
				"""{ "type": "kafka-producer",
				  |"params": {"topic": "test", "kafka": {} }
				  |}""".stripMargin
			val props = factory.getProps("kafka-producer", parse(json))
			props.get.actorClass should be(classOf[KafkaProducerActor[KafkaEncoder]])
		}

		"Provide a LogActor for type 'log'" in {
			val json =
				"""{
				  |"type": "log",
				  |"params": {"file": "afile"}
				  |}
				""".stripMargin
			val props = factory.getProps("log", parse(json))
			props.get.actorClass should be(classOf[LogActor])
		}

		"Provide a StatsActor for type 'stats'" in {
			val json =
				"""{ "type": "stats",
				  |"params": { "field": "val" }
				  |}""".stripMargin
			val props = factory.getProps("stats", parse(json))
			props.get.actorClass should be(classOf[StatsActor])
		}

		"Provide a ThresholdActor for type 'threshold'" in {
			val json =
				"""{ "type": "threshold",
				  |"params": { "key": "key1", "threshold": 1.618 }
				  |}""".stripMargin
			val props = factory.getProps("threshold", parse(json))
			props.get.actorClass should be(classOf[ThresholdActor])
		}

		"Provide a LinearRegressionActor for type 'linearregression'" in {
			val json =
				"""{ "type": "linearregression",
				  |"params": {"intercept": 0.1, "weights":{"salary": 0.43, "age": 1.8 }}
				  |}""".stripMargin
			val props = factory.getProps("linearregression", parse(json))
			props.get.actorClass should be(classOf[LinearRegressionActor])
		}

		"Provide a WindowActor for type 'window'" in {
			val json =
				"""{"type": "window",
				  |"params": { "method": "count", "number": 1 }
				  |}""".stripMargin
			val props = factory.getProps("window", parse(json))
			props.get.actorClass should be(classOf[WindowActor])
		}

		"Provide a ZscoreActor for type 'zscore'" in {
			val json = """{
				  | "type": "zscore",
				  | "name": "zscore1",
				  | "params": {
				  |   "field": "val",
				  |   "score": 3.141
				  | }, "collect": [
				  |   { "alias": "count", "from": "stats1", "field": "count" },
				  |   { "alias": "avg", "from": "stats1", "field": "avg" },
				  |   { "alias": "std", "from": "stats1", "field": "std" }
				  | ]
				  |}""".stripMargin
			val props = factory.getProps("zscore", parse(json))
			props.get.actorClass should be(classOf[ZscoreActor])
		}
	}
}