package io.coral.actors.transform

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.actors.Messages.{Shunt, Emit, Trigger}
import org.json4s.JsonAST.{JString, JDouble, JObject, JValue}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

class LookupActorSpec(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

    def this() = this(ActorSystem("StatsActorSpec"))

    override def afterAll() {
        TestKit.shutdownActorSystem(system)
    }

    implicit val timeout = Timeout(100.millis)

    "A Lookup actor" should {
        "Do nothing on missing function setting" in {
            val definition = parse( """ {
                "type": "lookup",
                "params": {
                  "key": "city",
                  "lookup": {
                    "amsterdam": { "country": "netherlands", "population": 800000 },
                    "vancouver": { "country": "canada", "population": 600000 }
                  }}}""").asInstanceOf[JObject]

            val props = CoralActorFactory.getProps(definition)
            assert(props == None)
        }

        "Do nothing on missing key setting" in {
            val definition = parse( """ {
                "type": "lookup",
                "params": {
                  "function": "enrich",
                  "lookup": {
                    "amsterdam": { "country": "netherlands", "population": 800000 },
                    "vancouver": { "country": "canada", "population": 600000 }
                  }}}""").asInstanceOf[JObject]

            val props = CoralActorFactory.getProps(definition)
            assert(props == None)
        }

        "Do nothing on missing lookup table" in {
            val definition = parse( """ {
                "type": "lookup",
                "params": {
                  "key": "city",
                  "function": "enrich"
                }}""").asInstanceOf[JObject]

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

        "Properly perform filtering on valid lookup data but missing input data" in {
            val lookup = getLookupActor("filter")

            val input = parse(
                """{
                    "city": "notinlookup",
                    "otherdata": "irrelevant",
                    "somevalue": 10
                }""").asInstanceOf[JObject]

            val actual = Await.result(lookup.ask(Shunt(input)), Timeout(1.seconds).duration)

            assert(actual == JNull)
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

    def getLookupActor(method: String): ActorRef = {
        val definition = parse(s""" {
            "type": "lookup",
            "params": {
              "key": "city",
              "function": "$method",
              "lookup": {
                "amsterdam": { "country": "netherlands", "population": 800000 },
                "vancouver": { "country": "canada", "population": 600000 }
              }}}""").asInstanceOf[JObject]

        val props = CoralActorFactory.getProps(definition).get
        val lookup = TestActorRef[LookupActor](props)
        lookup
    }
}