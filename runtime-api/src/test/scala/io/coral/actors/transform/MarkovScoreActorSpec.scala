package io.coral.actors.transform

import akka.actor.FSM.->
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import io.coral.actors.CoralActorFactory
import io.coral.api.DefaultModule
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.util.Timeout
import org.json4s.native.Serialization.write
import scala.collection
import scala.concurrent.duration._

class MarkovScoreActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {
  def this() = this(ActorSystem("MarkovScoreActorSpec"))
  implicit val timeout = Timeout(100.millis)
  implicit val injector = new DefaultModule(system.settings.config)

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  def createMarkovScoreActor(transitionProbs: Map[(String, String), Double]) = {
    implicit val formats = DefaultFormats
    val str =
      s"""{ "type": "actors",
         |"attributes":{
         |"type":"markovscore",
         |"params": { "transitionProbs": [${writeMapJson(transitionProbs)}]                                                }
          |}}}""".stripMargin
    val createJson   = parse(str).asInstanceOf[JObject]
    val props        = CoralActorFactory.getProps(createJson).get
    val actorTestRef = TestActorRef[MarkovScoreActor](props)

    val probe = TestProbe()
    actorTestRef.underlyingActor.emitTargets += probe.ref
    actorTestRef
  }

  def writeMapJson(map: Map[(String, String), Double]): String = {
    val objects = map.map{case ((source, destination), prob) => s"""{"source": "$source", "destination": "$destination", "prob": $prob}"""}
    objects.mkString(",")
  }

  "MarkovScoreActor" should {
    "Instantiate from companion object" in {
      val actor = createMarkovScoreActor(Map(("s00", "s01") -> 0.2))
      //println(pretty(actor.underlyingActor.jsonDef))
      actor.underlyingActor.transitionProbs should be (Map(("s00", "s01") -> 0.2))
    }

    "have no state" in {
      val actor = createMarkovScoreActor(Map(("s00", "s01") -> 0.2))
      actor.underlyingActor.state should be(Map.empty)
    }

    "have no timer action" in {
      val actor = createMarkovScoreActor(Map(("s00", "s01") -> 0.2))
      actor.underlyingActor.timer should be(actor.underlyingActor.noTimer)
    }

    "calculate Markov score of the click path with known states" in {
      val actor = createMarkovScoreActor(Map(("s00", "s01") -> 0.2, ("s01", "s02") -> 0.1))
      val message = parse(s"""{"transitions": ["s00", "s01", "s02"]}""").asInstanceOf[JObject]
      actor ! message
      actor.underlyingActor.result should be(0.2 * 0.1)
    }

    "return zero when click path contains unknown state" in {
      val actor = createMarkovScoreActor(Map(("s00", "s01") -> 0.2, ("s01", "s02") -> 0.1))
      val message = parse(s"""{"transitions": ["s00", "s01", "s03"]}""").asInstanceOf[JObject]
      actor ! message
      actor.underlyingActor.result should be(0.0)
    }
  }


}
