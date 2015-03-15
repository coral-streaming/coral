package io.coral.actors

// scala

import io.coral.actors.transform.ThresholdActor

import scala.concurrent.duration._

// akka
import akka.actor.ActorSystem
import akka.testkit.{TestActors, TestActorRef, TestKit, ImplicitSender}
import akka.util.Timeout
import akka.pattern.ask

// json
import org.json4s._
import org.json4s.jackson.JsonMethods._

// scalatest
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


class TestThresholdActor(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout = Timeout(100.millis)
  def this() = this(ActorSystem("TestThreshold"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }
  
  "A ThresholdActor" must {
    val createJson = parse(
      """{ "type": "threshold", "params":
      |{ "key": "key1", "threshold": 10.5 } }"""
      .stripMargin).asInstanceOf[JObject]

    // test invalid definition json as well !!!
    val props = CoralActorFactory.getProps(createJson).get
    val threshold = TestActorRef[ThresholdActor](props)

    // subscribe echo on the test actor
    threshold.underlyingActor.emitTargets += testActor

    "Emit when equal to the threshold" in {
      val json = parse("""{"key1": 10.5}""").asInstanceOf[JObject]
      threshold ! json
      expectMsg(parse("""{"key1": 10.5, "thresholdReached": "key1"}"""))
    }
    
    "Emit when higher than the threshold" in {
      val json = parse("""{"key1": 10.7}""").asInstanceOf[JObject]
      threshold ? json
      expectMsg(parse("""{"key1": 10.7, "thresholdReached": "key1"}"""))
    }

    "Not emit when lower than the threshold" in {
      val json = parse("""{"key1": 10.4}""").asInstanceOf[JObject]
      threshold ? json
      expectNoMsg
    }

    "Not emit when key is not present in triggering json" in {
      val json = parse("""{"key2": 10.7}""").asInstanceOf[JObject]
      threshold ! json
      expectNoMsg
    }

  }
}