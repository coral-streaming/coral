package io.coral.actors

import io.coral.actors.database.CassandraActor
import io.coral.actors.transform._
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{Matchers, WordSpecLike}

class CoralActorFactorySpec extends WordSpecLike with Matchers {

  implicit val formats = org.json4s.DefaultFormats

  "The CoralActorFactor" should {

    "Provide nothing for invalid JSON" in {
      val props = CoralActorFactory.getProps(parse( """{}"""))
      props should be(None)
    }

    "Provide a GroupByActor for any type with group by clause" in {
      val json =
        """{
          |"type": "stats",
          |"params": { "field": "val" },
          |"group": { "by": "somefield" }
          |}""".stripMargin
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[GroupByActor])
    }

    "Provide a CassandraActor for type 'cassandra'" in {
      val json =
        """{
          |"type": "cassandra",
          |           "seeds": ["0.0.0.0"], "keyspace": "test"
          |}""".stripMargin
      // should be: "params": { "seeds": ["0.0.0.0"], "keyspace": "test" }
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[CassandraActor])
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
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[FsmActor])
    }

    "Provide a GeneratorActor for type 'generator'" in {
      val json =
        """{
          |"type": "generator",
          |"format": {  }
          |"timer": { "rate": 1 }
          | }""".stripMargin
      // wrongly does not have params
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[GeneratorActor])
    }

    "Provide a HttpBroadcastActor for type 'httpbroadcast'" in {
      val json = """{ "type": "httpbroadcast" }""".stripMargin
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[HttpBroadcastActor])
    }

    "Provide a HttpClientActor for type 'httpclient'" in {
      val json = """{ "type": "httpclient" }""".stripMargin
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[HttpClientActor])
    }

    "Provide a StatsActor for type 'stats'" in {
      val json =
        """{
          |"type": "stats",
          |"params": { "field": "val" }
          |}""".stripMargin
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[StatsActor])
    }

    "Provide a ThresholdActor for type 'threshold'" in {
      val json =
        """{
          |"type": "threshold",
          |"params": { "key": "key1", "threshold": 1.618 }
          |}""".stripMargin
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[ThresholdActor])
    }

    "Provide a WindowActor for type 'window'" in {
      val json =
        """{
          |"type": "window",
          |"params": { "method": "count", "number": 1 }
          |}""".stripMargin
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[WindowActor])
    }

    "Provide a ZscoreActor for type 'zscore'" in {
      val json =
        """{
          |"type": "zscore",
          |"params": { "by": "tag", "field": "val", "score": 3.141 }
          |}""".stripMargin
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[ZscoreActor])
    }

  }

}
