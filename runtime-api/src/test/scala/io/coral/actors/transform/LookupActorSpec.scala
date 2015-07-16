package io.coral.actors.transform

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.actors.Messages.Shunt
import io.coral.api.DefaultModule
import org.json4s.JsonAST.JObject
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class LookupActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("LookupActorSpec"))

  implicit val injector = new DefaultModule(system.settings.config)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(100.millis)

  "A Lookup actor" should {
    "Do nothing on missing function setting" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "lookup",
                "params": {
                  "key": "city",
                  "lookup": {
                    "amsterdam": { "country": "netherlands", "population": 800000 },
                    "vancouver": { "country": "canada", "population": 600000 }
                  }}}}""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition)
      assert(props == None)
    }

    "Do nothing on missing key setting" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "lookup",
                "params": {
                  "function": "enrich",
                  "lookup": {
                    "amsterdam": { "country": "netherlands", "population": 800000 },
                    "vancouver": { "country": "canada", "population": 600000 }
                  }}}}""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition)
      assert(props == None)
    }

    "Do nothing on missing lookup table" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "lookup",
                "params": {
                  "key": "city",
                  "function": "enrich"
                }}}""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition)
      assert(props == None)
    }

    "Properly perform enrichment on valid lookup data and valid input data" in {
      val lookup = getLookupActor("enrich")

      val input = parse(
        """{
                    "city": "amsterdam",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

      val actual = Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)

      val expected = parse(
        """{
                  "city": "amsterdam",
                  "otherdata": "irrelevant",
                  "somevalue": 10,
                  "country": "netherlands",
                  "population": 800000
                }""").asInstanceOf[JObject]

      assert(actual == expected)
    }

    "Emit nothing for check when lookup value does not match any entry in the table" in {
      val lookup = getLookupActor("check")
      val actor = lookup.underlyingActor
      val input = parse("""{"city": "does not exist"}""").asInstanceOf[JObject]
      actor.simpleEmitTrigger(input) should be(Some(JNothing))
    }

    "Emit default value for check when lookup value does not match any entry in the table" in {
      val defaultValue = """{"country": "unknown", "population": 0}"""
      val lookup = getLookupActor("check", Some("exact"), Some(defaultValue))
      val actor = lookup.underlyingActor
      val input = parse("""{"city": "does not exist"}""").asInstanceOf[JObject]
      actor.simpleEmitTrigger(input) should be(Some(parse(defaultValue)))
    }

    "Emit default value using template for check when lookup value does not match any entry in the table" in {
      val defaultValue = """{"country": "unknown", "population": 0, "province": "${province}"}"""
      val lookup = getLookupActor("check", Some("exact"), Some(defaultValue))
      val actor = lookup.underlyingActor
      val input = parse("""{"city": "does not exist", "province": "noord-holland"}""").asInstanceOf[JObject]
      actor.simpleEmitTrigger(input) should be(Some(parse(defaultValue.replace("${province}", "noord-holland"))))
    }

    "Emit unenriched input for function 'enrich' when lookup value does not match any entry in the table" in {
      val lookup = getLookupActor("enrich")
      val actor = lookup.underlyingActor
      val input = parse("""{"city": "does not exist"}""").asInstanceOf[JObject]
      actor.simpleEmitTrigger(input) should be(Some(input))
    }

    "Properly perform checking on valid lookup data and valid input data" in {
      val lookup = getLookupActor("check")

      val input = parse(
        """{
                    "city": "amsterdam",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

      val actual = Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)

      val expected = parse(
        """{
                    "country": "netherlands",
                    "population": 800000
                }""").asInstanceOf[JObject]

      assert(actual == expected)
    }

    "Properly perform filtering on valid lookup data and valid input data" in {
      val lookup = getLookupActor("filter")

      val input = parse(
        """{
                    "city": "amsterdam",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

      val actual = Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)

      assert(actual == input)
    }

    "Properly perform filtering on valid lookup data and valid input data with starts with match" in {
      val lookup = getLookupActor("filter", Some("startswith"))

      val input = parse(
        """{
                    "city": "amsterdam, noord-holland",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

      val actual = Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)

      assert(actual == input)
    }

    "Properly perform filtering on valid lookup data but missing input data" in {
      val lookup = getLookupActor("filter")

      val input = parse(
        """{
                    "city": "notinlookup",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

      val actual = Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)

      assert(actual == JNothing)
    }

    "Do no enrichment on valid lookup data but invalid input data" in {
      val lookup = getLookupActor("enrich")

      val input = parse(
        """{
                    "notcity": "notcityvalue",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

      intercept[akka.pattern.AskTimeoutException] {
        Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)
      }
    }

    "Do no checking on valid lookup data but invalid input data" in {
      val lookup = getLookupActor("check")

      val input = parse(
        """{
                    "notcity": "notcityvalue",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

      intercept[akka.pattern.AskTimeoutException] {
        Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)
      }
    }

    "Do no filtering on valid lookup data but invalid input data" in {
      val lookup = getLookupActor("filter")

      val input = parse(
        """{
                    "notcity": "notcityvalue",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

      intercept[akka.pattern.AskTimeoutException] {
        Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)
      }
    }
  }

  def getLookupActor(method: String, matchType: Option[String] = None, defaultValue: Option[String] = None) = {
    val matchDefinition = matchType match {
      case Some(matchType) =>
        s"""
        "match":"${matchType}",
        """
      case None => ""
    }
    val defaultValueDefinition = defaultValue match {
      case Some(defaultValue) =>
        s"""
        "default":${defaultValue},
        """.stripMargin
      case None => ""
    }
    val definition = parse( s""" {
            "type": "actors",
            "attributes": {"type": "lookup",
            "params": {
              "key": "city",
              "function": "$method",
              $matchDefinition
              $defaultValueDefinition
              "lookup": {
                "amsterdam": { "country": "netherlands", "population": 800000 },
                "vancouver": { "country": "canada", "population": 600000 }
              }}}}""").asInstanceOf[JObject]

    val props = CoralActorFactory.getProps(definition).get
    val lookup = TestActorRef[LookupActor](props)
    lookup
  }
}