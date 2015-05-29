package io.coral.actors.transform

// scala

import io.coral.actors.CoralActorFactory
import io.coral.api.DefaultModule
import scala.concurrent.duration._
// akka
import akka.actor.ActorSystem
import akka.testkit._
import akka.util.Timeout

// json
import org.json4s._
import org.json4s.jackson.JsonMethods._

// scalatest
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class ThresholdActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout = Timeout(100.millis)
  def this() = this(ActorSystem("ThresholdActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }
  
  "A ThresholdActor" must {
    val createJson = parse(
      """{ "type": "actors", "attributes": {"type": "threshold", "params":
      |{ "key": "key1", "threshold": 10.5 } } }"""
      .stripMargin).asInstanceOf[JObject]
    
    implicit val injector = new DefaultModule(system.settings.config)

    // test invalid definition json as well !!!
    val props = CoralActorFactory.getProps(createJson).get
    val threshold = TestActorRef[ThresholdActor](props)

    // subscribe the testprobe for emitting
    val probe = TestProbe()
    threshold.underlyingActor.emitTargets += probe.ref

    "Emit when equal to the threshold" in {
      val json = parse("""{"key1": 10.5}""").asInstanceOf[JObject]
      threshold ! json
      probe.expectMsg(parse("""{"key1": 10.5, "thresholdReached": "key1"}"""))
    }
    
    "Emit when higher than the threshold" in {
      val json = parse("""{"key1": 10.7}""").asInstanceOf[JObject]
      threshold ! json
      probe.expectMsg(parse("""{"key1": 10.7, "thresholdReached": "key1"}"""))
    }

    "Not emit when lower than the threshold" in {
      val json = parse("""{"key1": 10.4}""").asInstanceOf[JObject]
      threshold ! json
      probe.expectNoMsg()
    }

    "Not emit when key is not present in triggering json" in {
      val json = parse("""{"key2": 10.7}""").asInstanceOf[JObject]
      threshold ! json
      probe.expectNoMsg()
    }

  }
}