package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.api.DefaultModule
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class StatsActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("StatsActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(100.millis)
  JValue
  
  implicit val injector = new DefaultModule(system.settings.config)

  def createStatsActor: StatsActor = {
    val createJson = parse( """{ "type": "actors", "subtype": "stats", "params": { "field": "val" } }""")
      .asInstanceOf[JObject]
    val props = CoralActorFactory.getProps(createJson).get
    val actorRef = TestActorRef[StatsActor](props)
    actorRef.underlyingActor
  }

  val expectedInitialState = Map(
    ("count", render(0L)),
    ("avg", render(JNull)),
    ("sd", render(JNull)),
    ("min", render(JNull)),
    ("max", render(JNull))
  )

  "StatsActor" should {

    "have a field corresponding to the json definition" in {
      val actor = createStatsActor
      actor.field should be("val")
    }

    "supply it's state" in {
      val actor = createStatsActor
      actor.state should be(expectedInitialState)
    }

    "accept a value as trigger" in {
      val actor = createStatsActor
      val triggerJson = parse( """{ "bla": 1.0, "val": 2.7 }""").asInstanceOf[JObject]
      actor.trigger(triggerJson)
      actor.state should be(
        Map(
          ("count", render(1L)),
          ("avg", render(2.7)),
          ("sd", render(0.0)),
          ("min", render(2.7)),
          ("max", render(2.7))
        ))
    }

    "have timer reset statistics" in {
      val actor = createStatsActor
      val triggerJson = parse( """{ "val": 2.7 }""").asInstanceOf[JObject]
      actor.trigger(triggerJson)
      actor.state should be(
        Map(
          ("count", render(1L)),
          ("avg", render(2.7)),
          ("sd", render(0.0)),
          ("min", render(2.7)),
          ("max", render(2.7))
        ))
      val json = actor.timer
      json should be(JNothing)
      actor.state should be(expectedInitialState)
    }

    "emit nothing" in {
      val actor = createStatsActor
      actor.emit should be(actor.emitNothing)
    }

  }

}
