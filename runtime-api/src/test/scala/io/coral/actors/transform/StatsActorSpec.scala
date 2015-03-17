package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.json.pimpAny

import scala.concurrent.duration._

class StatsActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout = Timeout(100.millis)

  def this() = this(ActorSystem("TestThreshold"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "StatsActor" should {

    val createJson = parse( """{ "type": "stats", "params": { "field": "val" } }""")
      .asInstanceOf[JObject]
    val props = CoralActorFactory.getProps(createJson).get
    val actorRef = TestActorRef[StatsActor](props)
    val actor = actorRef.underlyingActor

    "have a field corresponding to the json definition" in {
      actor.field should be("val")
    }

    "supply it's state" in {
      actor.state should be(
        Map(
          ("count", render(0L)),
          ("avg", render(JNull)),
          ("sd", render(JNull)),
          ("min", render(JNull)),
          ("max", render(JNull))
        ))
    }

    "accept a value as trigger" in {
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
      //val triggerJson2 = parse("""{ "val": 2.7 }""").asInstanceOf[JObject]
    }

  }

}
