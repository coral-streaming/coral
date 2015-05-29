package io.coral.actors.transform

import akka.actor.{ActorSystem, IllegalActorStateException}
import akka.pattern.ask
import akka.testkit._
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.actors.Messages.GetField
import io.coral.api.DefaultModule
import org.json4s.JsonAST.{JInt, JString}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class GeneratorActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout = Timeout(1.seconds)
  implicit val injector = new DefaultModule(system.settings.config)
  val duration = timeout.duration

  def this() = this(ActorSystem("testSystem"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A Generator actor" should {
    "Create data based on an input format" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                    "rate": 10,
                    "times": 1,
                    "delay": 0
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      val value = probe.receiveOne(3.second)
      value match {
        case JObject(List(
        ("field1", JDouble(_)),
        ("field2", JString(_)),
        ("field3", JDouble(_)))) =>
        // Do nothing, success
        case other =>
          fail("Invalid tree created")
      }
    }

    "Handle floating point numbers and non-floating point numbers equally well" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100.25, 10.53)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100.743)"
                }, "timer": {
                    "rate": 10,
                    "times": 1,
                    "delay": 0
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      probe.receiveOne(1.second) match {
        case JObject(List(
        ("field1", JDouble(_)),
        ("field2", JString(_)),
        ("field3", JDouble(_)))) =>
        // Do nothing, success
        case other =>
          fail("Invalid tree created")
      }
    }

    "Handle nested JSON objects" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100.25, 10.53)",
                    "field2": "['a', 'b', 'c']",
                    "field3": {
                       "nested1": "U(100.743)",
                       "nested2": "U(20.3)",
                       "nested3": "N(20, 1)"
                    }
                }, "timer": {
                    "rate": 10,
                    "times": 1,
                    "delay": 0
                }
            } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      probe.receiveOne(1.second) match {
        case JObject(List(("field1", JDouble(_)),
        ("field2", JString(_)),
        ("field3", JObject(List(
          ("nested1", JDouble(_)),
          ("nested2", JDouble(_)),
          ("nested3", JDouble(_))))))) =>
        // Do nothing, success
        case other =>
          fail("Invalid tree created")
      }
    }

    "Emit nothing on invalid generator function 'F'" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "F(100.25, 10.53)",
                    "field2": "['a', 'b', 'c']",
                    "field3": {
                       "nested1": "F(100.743)",
                       "nested2": "F(20.3)",
                       "nested3": "F(20, 1)"
                    }
                }, "timer": {
                    "rate": 10,
                    "times": 1,
                    "delay": 0
                }
            } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      // Generates JNothing objects which are not received
      probe.expectNoMsg()
    }

    "Emit nothing on improperly structured generator function 'N'" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100.))25, 10.53)",
                    "field2": "['a', 'b', 'c']",
                    "field3": {
                       "nested1": "U(100.743)",
                       "nested2": "N(20.3)",
                       "nested3": "F(20, 1)"
                    }
                }, "timer": {
                    "rate": 10,
                    "times": 1,
                    "delay": 0
                }
            } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      probe.expectNoMsg()
    }

    "Emit nothing on improperly structured generator function with a list" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100.25, 10.53)",
                    "field2": "[[''a', 'b,, 'c']",
                }, "timer": {
                    "rate": 10,
                    "times": 1,
                    "delay": 0
                }
            } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      probe.expectNoMsg()
    }

    "Emit JNothing on empty generator function with a list" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100.25, 10.53)",
                    "field2": "[]",
                }, "timer": {
                    "rate": 10,
                    "times": 1,
                    "delay": 0
                }
            } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      probe.expectNoMsg()
    }

    "Do nothing on negative rate definition" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                    "rate": -20,
                    "times": 1,
                    "delay": 0
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition)
      assert(props == None)
    }

    "Do nothing on non-integer rate definition" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                    "rate": "notAnInteger",
                    "times": 1,
                    "delay": 0
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition)
      assert(props == None)
    }

    "Do nothing on missing rate definition" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                    "times": 1,
                    "delay": 0
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition)
      assert(props == None)
    }

    "Do not emit anything on times definition smaller than or equal to zero" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                    "rate": 20,
                    "times": -100,
                    "delay": 0
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()

      // Test here that the actor does not exist any more
      intercept[IllegalActorStateException] {
        generator.underlyingActor.emitTargets += probe.ref
        probe.expectNoMsg()
      }
    }

    "Set delay to 0 if delay smaller than 0 is given" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                    "rate": 20,
                    "times": 1,
                    "delay": -3000
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      val method = Await.result(generator.ask(GetField("rate")), duration)
      assert(method == JDouble(20.0))

      val number = Await.result(generator.ask(GetField("times")), duration)
      assert(number == JInt(1))

      val sliding = Await.result(generator.ask(GetField("delay")), duration)
      assert(sliding == JDouble(0.0))
    }

    "Only emit 3 items when times is set to 3" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                   "rate": 100,
                   "times": 3
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      // Should receive 3 messages within 30 milliseconds
      val value = probe.receiveN(3, Timeout(100.millis).duration).toSeq
      value match {
        case Seq(
          JObject(List(
            ("field1", JDouble(_)),
            ("field2", JString(_)),
            ("field3", JDouble(_)))),
          JObject(List(
            ("field1", JDouble(_)),
            ("field2", JString(_)),
            ("field3", JDouble(_)))),
          JObject(List(
            ("field1", JDouble(_)),
            ("field2", JString(_)),
            ("field3", JDouble(_))))) =>
        // Do nothing, success
        case other =>
          fail("Invalid tree created")
      }

      expectNoMsg()
    }

    "Immediately send something if delay is not set" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a','b','c']",
                    "field3": "U(100)"
                }, "timer": {
                    "rate": 10,
                    "times": 1
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      // Still allow some time to receive message
      val value = probe.receiveOne(500.millis)
      value match {
        case JObject(List(
          ("field1", JDouble(_)),
          ("field2", JString(_)),
          ("field3", JDouble(_)))) =>
        // Do nothing, success
        case other =>
          println(other)
          fail("Invalid tree created")
      }
    }

    "Wait the correct amount of time before sending first message if delay is set" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                    "rate": 10,
                    "times": 1,
                    "delay": 3000
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      probe.expectNoMsg(3.seconds)

      val value = probe.receiveOne(1.seconds)
      value match {
        case JObject(List(
          ("field1", JDouble(_)),
          ("field2", JString(_)),
          ("field3", JDouble(_)))) =>
        // Do nothing, success
        case other =>
          fail("Invalid tree created")
      }
    }

    "Do not emit anything after the times limit has been reached" in {
      val definition = parse( """ {
                "type": "actors",
                "attributes": {"type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                    "rate": 100,
                    "times": 3,
                    "delay": 0
                } } }""").asInstanceOf[JObject]

      val props = CoralActorFactory.getProps(definition).get
      val generator = TestActorRef[GeneratorActor](props)
      val probe = TestProbe()
      generator.underlyingActor.emitTargets += probe.ref

      // Should receive 3 messages within 30 milliseconds
      val value = probe.receiveN(3, Timeout(100.millis).duration).toSeq
      value match {
        case Seq(
          JObject(List(
            ("field1", JDouble(_)),
            ("field2", JString(_)),
            ("field3", JDouble(_)))),
          JObject(List(
            ("field1", JDouble(_)),
            ("field2", JString(_)),
            ("field3", JDouble(_)))),
          JObject(List(
            ("field1", JDouble(_)),
            ("field2", JString(_)),
            ("field3", JDouble(_))))) =>
        // Do nothing, success
        case other =>
          fail("Invalid tree created")
      }

      // Should not expect anything after this any more
      probe.expectNoMsg()
    }
  }
}