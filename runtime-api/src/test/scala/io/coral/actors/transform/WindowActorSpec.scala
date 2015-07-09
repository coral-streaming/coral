package io.coral.actors.transform

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{TestActorRef, ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import io.coral.actors.Messages._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class WindowActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout = Timeout(100.seconds)
  val duration = timeout.duration

  def this() = this(ActorSystem("WindowActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A WindowActor" should {
    "Do not create a new actor with an improper definition" in {
      val constructor = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"invalid", "number": 3, "sliding": 1 }}}""".stripMargin).asInstanceOf[JObject]

      intercept[IllegalArgumentException] {
        val windowActor = WindowActor(constructor)
        assert(windowActor == None)
      }
    }

    "Do not create a new actor with negative sliding window" in {
      val constructor = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"count", "number": 3, "sliding": -3 }}}""".stripMargin).asInstanceOf[JObject]

      intercept[IllegalArgumentException] {
        val windowActor = WindowActor(constructor)
        assert(windowActor == None)
      }
    }

    "Do not create a new actor with a negative number" in {
      val constructor = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"count", "number": -3, "sliding": 3 }}}""".stripMargin).asInstanceOf[JObject]

      intercept[IllegalArgumentException] {
        val windowActor = WindowActor(constructor)
        assert(windowActor == None)
      }
    }

    "Do not create a new actor with a floating point number parameter" in {
      val constructor = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"count", "number": 4.42, "sliding": 3 }}}""".stripMargin).asInstanceOf[JObject]

      intercept[IllegalArgumentException] {
        val windowActor = WindowActor(constructor)
        assert(windowActor == None)
      }
    }

    "Do not create a new actor with a floating point sliding parameter" in {
      val constructor = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"count", "number": 4, "sliding": 3.42 }}}""".stripMargin).asInstanceOf[JObject]

      intercept[IllegalArgumentException] {
        def windowActor = WindowActor(constructor)
        assert(windowActor == None)
      }
    }

    "Automatically set a sliding window with only number definition" in {
      val constructor = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"count", "number": 3 }}}""".stripMargin).asInstanceOf[JObject]

      val windowActor = system.actorOf(Props(new WindowActor(constructor)))

      val method = Await.result(windowActor.ask(GetField("method")), duration)
      assert(method == JString("count"))

      val number = Await.result(windowActor.ask(GetField("number")), duration)
      assert(number == JInt(3))

      val sliding = Await.result(windowActor.ask(GetField("sliding")), duration)
      assert(sliding == JInt(3))
    }

    // ('count', 3, 3) means a window which collects 3 events and then emits
    // the collected values, and then clears the list and starts over
    "Properly perform ('count', 3, 3)" in {
      val construct = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"count", "number": 3, "sliding": 3 } } }""".stripMargin).asInstanceOf[JObject]

      val props = WindowActor(construct).get
      val windowActor = TestActorRef[WindowActor](props)

      // subscribe the testprobe for emitting
      val probe = TestProbe()
      windowActor.underlyingActor.emitTargets += probe.ref

      for (i <- 1 to 4) {
        val json = parse( s"""{ "name": "object$i" } """)
        windowActor ! (json.asInstanceOf[JObject])
        Thread.sleep(300)
      }

      var expected = parse(
        """{ "data": [ { "name" : "object1" },
                     { "name" : "object2" },
                     { "name" : "object3" } ] } """)
      probe.expectMsg(expected)

      var json = parse( s"""{ "name": "object5" } """).asInstanceOf[JObject]
      windowActor ! json

      json = parse( s"""{ "name": "object6" } """).asInstanceOf[JObject]
      windowActor ! json

      expected = parse(
        """{ "data": [ { "name" : "object4" },
                     { "name" : "object5" },
                     { "name" : "object6" } ] }""")
      probe.expectMsg(expected)

      json = parse( s"""{ "name": "object7" } """).asInstanceOf[JObject]
      windowActor ! json
      json = parse( s"""{ "name": "object8" } """).asInstanceOf[JObject]
      windowActor ! json
      json = parse( s"""{ "name": "object9" } """).asInstanceOf[JObject]
      windowActor ! json

      expected = parse(
        """{ "data": [ { "name" : "object7" },
                     { "name" : "object8" },
                     { "name" : "object9" } ] } """)
      probe.expectMsg(expected)
    }

    "Properly perform ('count', 3, 1)" in {
      val construct = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"count", "number": 3, "sliding": 1 } } }""".stripMargin).asInstanceOf[JObject]

      val props = WindowActor(construct).get
      val windowActor = TestActorRef[WindowActor](props)

      // subscribe the testprobe for emitting
      val probe = TestProbe()
      windowActor.underlyingActor.emitTargets += probe.ref

      val method = Await.result(windowActor.ask(GetField("method")), duration)
      assert(method == JString("count"))

      val number = Await.result(windowActor.ask(GetField("number")), duration)
      assert(number == JInt(3))

      val sliding = Await.result(windowActor.ask(GetField("sliding")), duration)
      assert(sliding == JInt(1))

      for (i <- 1 to 3) {
        val json = parse(
          s"""
             |{ "name": "object$i" }
             |""".stripMargin).asInstanceOf[JObject]
        windowActor ! json
        Thread.sleep(300)
      }

      var expected = parse(
        """{ "data": [ { "name" : "object1" },
                     { "name" : "object2" },
                     { "name" : "object3" } ] }""")
      probe.expectMsg(expected)

      // Queue contains only one item so expect no response
//      emitted = Await.result(windowActor.ask(Emit()), duration)
//      assert(emitted == JNothing)

      var json = parse( s"""{ "name": "object4" } """).asInstanceOf[JObject]
      windowActor ! json

      expected = parse(
        """{ "data": [ { "name" : "object2" },
                     { "name" : "object3" },
                     { "name" : "object4" } ] }""")
      probe.expectMsg(expected)

      json = parse( s"""{ "name": "object5" } """).asInstanceOf[JObject]
      windowActor ! json
      expected = parse(
        """{ "data": [ { "name" : "object3" },
                     { "name" : "object4" },
                     { "name" : "object5" } ] }""")
      probe.expectMsg(expected)
    }

    "Properly perform ('time', 1, 1)" in {
      val construct = parse(
        """
          | { "type": "actors", "attributes": {"type": "window",
          | "params" : { "method": "time", "number": 1000, "sliding": 1000 }}}
          | """.stripMargin).asInstanceOf[JObject]

      val props = WindowActor(construct).get
      val windowActor = TestActorRef[WindowActor](props)

      // subscribe the testprobe for emitting
      val probe = TestProbe()
      windowActor.underlyingActor.emitTargets += probe.ref

      for (i <- 1 to 10) {
        val json = parse( s"""{ "name": "object$i" } """).asInstanceOf[JObject]
        windowActor ! json
        Thread.sleep(300)
      }

      var expected = parse(
        """{ "data": [
          |{ "name" : "object1" },
          |{ "name" : "object2" },
          |{ "name" : "object3" },
          |{ "name" : "object4" }]}""".stripMargin)
      probe.expectMsg(Timeout(2.seconds).duration, expected)

      expectNoMsg(1.second)

      expected = parse(
        """{ "data": [
          |{ "name" : "object5" },
          |{ "name" : "object6" },
          |{ "name" : "object7" }]}""".stripMargin)
      probe.expectMsg(Timeout(2.seconds).duration, expected)
    }

    "Properly perform ('time', 3, 1)" in {
      val construct = parse(
        """
          | { "type": "actors", "attributes": {"type": "window",
          | "params" : { "method": "time", "number": 3000, "sliding": 1000 }}}
          | """.stripMargin).asInstanceOf[JObject]

      val props = WindowActor(construct).get
      val windowActor = TestActorRef[WindowActor](props)

      // subscribe the testprobe for emitting
      val probe = TestProbe()
      windowActor.underlyingActor.emitTargets += probe.ref

      for (i <- 1 to 10) {
        val json = parse(s"""{ "name": "object$i" } """.stripMargin).asInstanceOf[JObject]
        windowActor ! json
        Thread.sleep(1000)
      }

      var expected = parse(
        """{ "data": [
          |{ "name" : "object1" },
          |{ "name" : "object2" },
          |{ "name" : "object3" }]}""".stripMargin)
      probe.expectMsg(Timeout(4.seconds).duration, expected)

      expected = parse(
        """{ "data": [
          |{ "name" : "object2" },
          |{ "name" : "object3" },
          |{ "name" : "object4" }]}""".stripMargin)
      probe.expectMsg(Timeout(7.seconds).duration, expected)
    }

    "Not emit the window before the window size is reached" in {
      val construct = parse(
        """{ "type": "actors", "attributes": {"type": "window", "params" : { "method":
          |"time", "number": 10000, "sliding": 1000 }}}""".stripMargin).asInstanceOf[JObject]

      val props = WindowActor(construct).get
      val windowActor = TestActorRef[WindowActor](props)

      val probe = TestProbe()
      windowActor.underlyingActor.emitTargets += probe.ref

      for (i <- 1 to 3) {
        val json = parse( s"""{ "name": "object$i" } """).asInstanceOf[JObject]
        windowActor ! json
      }

      probe.expectNoMsg(1.seconds)
    }
  }
}