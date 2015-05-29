package io.coral.actors.transform

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import io.coral.lib.{NotSoRandom, Random}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps

class SampleActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  def this() = this(ActorSystem("SampleActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  def arbitrarySampleActor(): SampleActor = {
    val json = parse(
      """{ "type": "actors",
        | "attributes": {"type": "sample",
        | "params": { "fraction": 0.707 } } }
      """.stripMargin)
    val props = SampleActor(json).get
    TestActorRef[SampleActor](props).underlyingActor
  }

  def notSoRandomSampleActor(fraction: Double, randoms: Double*): SampleActor = {
    val json = parse(
      s"""{ "type": "actors",
         |"attributes": {"type": "sample",
         |"params": { "fraction": ${fraction} } } }
      """.stripMargin)
    val source = NotSoRandom(randoms: _*)
    val props = Props(classOf[SampleActor], json, Random(source))
    TestActorRef[SampleActor](props).underlyingActor
  }

  implicit val timeout = Timeout(100 millis)

  "A SampleActor" should {

    "Be instantiated with sample fraction" in {
      val json = parse(
        """{ "type": "actors",
          | "attributes": {"type": "sample",
          | "params": { "fraction": 0.5 } } }
        """.stripMargin)
      val props = SampleActor(json).get
      props.actorClass() should be(classOf[SampleActor])
      val actor = TestActorRef[SampleActor](props).underlyingActor
      actor.fraction should be(0.5)
    }

    "Be instantiated with sample percentage" in {
      val json = parse(
        """{ "type": "actors",
          | "attributes": {"type": "sample",
          | "params": { "percentage": 25.6 } } }
        """.stripMargin)
      val props = SampleActor(json).get
      val actor = TestActorRef[SampleActor](props).underlyingActor
      actor.fraction should be(0.256)
    }

    "Not be instantiated without fraction or percentage" in {
      val json = parse(
        """{ "type": "actors",
          | "attributes": {"type": "sample",
          | "params": { "bla": "blabla" } } }
        """.stripMargin)
      SampleActor(json) should be(None)
    }

    "Have no time function implemented" in {
      val actor = arbitrarySampleActor()
      actor.timer should be(JNothing)
    }

    "Have no state for collection" in {
      val actor = arbitrarySampleActor()
      actor.state should be(Map.empty[String, JValue])
    }

    "Be constructible with a io.coral.lib.Random for random boolean stream" in {
      val actor = notSoRandomSampleActor(fraction = 0.5, randoms = 0.1, 0.49, 0.50, 0.51, 0.8, 0.4)
      actor.next() should be(true)
      actor.next() should be(true)
      actor.next() should be(false)
      actor.next() should be(false)
      actor.next() should be(false)
      actor.next() should be(true)
    }

    "Should trigger true or false according to random binomial sequence" in {
      val actor = notSoRandomSampleActor(fraction = 0.7, randoms = 0.8, 0.6)
      val json = parse( """{ "something": "whatever" }""").asInstanceOf[JObject]
      val trigger1 = actor.trigger(json)
      whenReady(trigger1.run) {
        result => {
          result should be(Some({}))
          actor.pass should be(false)
        }
      }
      val trigger2 = actor.trigger(json)
      whenReady(trigger2.run) { _ => actor.pass should be(true) }
    }

    "Should emit only when pass is true" in {
      val json = parse( """{ "something": "whatever" }""").asInstanceOf[JObject]
      val actor = arbitrarySampleActor()
      actor.pass = false
      actor.emit(json) should be(JNothing)
      actor.pass = true
      actor.emit(json) should be(json)
    }

    "Should have trigger and emit cooperate" in {
      val actor = notSoRandomSampleActor(fraction = 0.7, randoms = 0.6, 0.8)
      val ref = actor.self
      val json = parse( """{ "something": "whatever" }""").asInstanceOf[JObject]
      val probe = TestProbe()
      actor.emitTargets += probe.ref
      ref ! json
      probe.expectMsg(json)
      ref ! json
      probe.expectNoMsg(100 millis)
    }

  }

}
