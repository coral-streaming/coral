package io.coral.actors

import io.coral.actors.connector.{KafkaProducerActor, KafkaConsumerActor}
import io.coral.actors.database.CassandraActor
import io.coral.actors.transform._
import org.json4s.native.JsonMethods._
import org.scalatest.{Matchers, WordSpecLike}
import scala.language.postfixOps

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
          |"type": "actors",
          |"attributes": {"type": "cassandra",
          |"seeds": ["0.0.0.0"], "keyspace": "test"
          |}}""".stripMargin
      // should be: "params": { "seeds": ["0.0.0.0"], "keyspace": "test" }
      val props = factory.getProps("cassandra", parse(json))
      props.get.actorClass should be(classOf[CassandraActor])
    }

    "Provide an FsmActor for type 'fsm'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "fsm",
          |"params": {
          |   "key": "transactionsize",
          |   "table": {
          |     "normal": {
          |       "small": "normal"
          |     }
          |   },
          |   "s0": "normal"
          |}}}""".stripMargin
      val props = factory.getProps("fsm", parse(json))
      props.get.actorClass should be(classOf[FsmActor])
    }

    "Provide a GeneratorActor for type 'generator'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "generator",
          |"format": {  }
          |"timer": { "rate": 1 }
          | }}""".stripMargin
      // wrongly does not have params
      val props = factory.getProps("generator", parse(json))
      props.get.actorClass should be(classOf[GeneratorActor])
    }

    "Provide a HttpBroadcastActor for type 'httpbroadcast'" in {
      val json = """{ "type": "actors", "attributes": {"type": "httpbroadcast" } }""".stripMargin
      val props = factory.getProps("httpbroadcast", parse(json))
      props.get.actorClass should be(classOf[HttpBroadcastActor])
    }

    "Provide a HttpClientActor for type 'httpclient'" in {
      val json = """{ "type": "actors", "attributes": {"type": "httpclient", "params": {"method": "GET", "url": "http://localhost" } } }""".stripMargin
      val props = factory.getProps("httpclient", parse(json))
      props.get.actorClass should be(classOf[HttpClientActor])
    }

    "Provide a SampleActor for type 'threshold'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "sample",
          |"params": { "fraction": 0.1010010001 }
          |}}""".stripMargin
      val props = factory.getProps("sample", parse(json))
      props.get.actorClass should be(classOf[SampleActor])
    }

    "Provide a KafkaConsumerActor for type 'kafka-consumer'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "kafka-consumer",
          |"params": { "topic": "bla", "kafka": {} }
          |}}""".stripMargin
      val props = factory.getProps("kafka-consumer", parse(json))
      props.get.actorClass should be(classOf[KafkaConsumerActor])
    }

    "Provide a KafkaProducerActor for type 'kafkaproducer'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "kafka-producer",
          |"params": {"topic": "test", "kafka": {} }
          |}}""".stripMargin
      val props = factory.getProps("kafka-producer", parse(json))
      props.get.actorClass should be(classOf[KafkaProducerActor])
    }

    "Provide a StatsActor for type 'stats'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "stats",
          |"params": { "field": "val" }
          |}}""".stripMargin
      val props = factory.getProps("stats", parse(json))
      props.get.actorClass should be(classOf[StatsActor])
    }

    "Provide a ThresholdActor for type 'threshold'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "threshold",
          |"params": { "key": "key1", "threshold": 1.618 }
          |}}""".stripMargin
      val props = factory.getProps("threshold", parse(json))
      props.get.actorClass should be(classOf[ThresholdActor])
    }

    "Provide a WindowActor for type 'window'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "window",
          |"params": { "method": "count", "number": 1 }
          |}}""".stripMargin
      val props = factory.getProps("window", parse(json))
      props.get.actorClass should be(classOf[WindowActor])
    }

    "Provide a ZscoreActor for type 'zscore'" in {
      val json =
        """{
          |"type": "actors",
          |"attributes": {"type": "zscore",
          |"params": { "by": "tag", "field": "val", "score": 3.141 }
          |}}""".stripMargin
      val props = factory.getProps("zscore", parse(json))
      props.get.actorClass should be(classOf[ZscoreActor])
    }
  }
}
