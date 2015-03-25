package io.coral.actors.transform

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.{TestActorRef, TestProbe, ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.actors.Messages.{GetField, Shunt}
import org.json4s.JsonAST.JValue
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

class TestGeneratorActor(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {
    implicit val timeout = Timeout(1.seconds)
    val duration = timeout.duration

    def this() = this(ActorSystem("testSystem"))

    "A Generator actor" should {
        "Create data based on an input format" in {
            val definition = parse( """ {
                "type": "generator",
                "format": {
                    "field1": "N(100, 10)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100)"
                }, "timer": {
                   "rate": 10,
                    "times": 100,
                    "delay": 2
                } } """).asInstanceOf[JObject]

            val props = CoralActorFactory.getProps(definition).get
            val generator = TestActorRef[GeneratorActor](props)
            val probe = TestProbe()
            generator.underlyingActor.emitTargets += probe.ref

            probe.receiveOne(3.second) match {
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
                "type": "generator",
                "format": {
                    "field1": "N(100.25, 10.53)",
                    "field2": "['a', 'b', 'c']",
                    "field3": "U(100.743)"
                }, "timer": {
                   "rate": 10,
                    "times": 100,
                    "delay": 2
                } } """).asInstanceOf[JObject]

            val props = CoralActorFactory.getProps(definition).get
            val generator = TestActorRef[GeneratorActor](props)
            val probe = TestProbe()
            generator.underlyingActor.emitTargets += probe.ref

            probe.expectNoMsg(2 seconds)

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
                "type": "generator",
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
                    "times": 100,
                    "delay": 2
                }
            } """).asInstanceOf[JObject]

            val props = CoralActorFactory.getProps(definition).get
            val generator = TestActorRef[GeneratorActor](props)
            val probe = TestProbe()
            generator.underlyingActor.emitTargets += probe.ref

            probe.expectNoMsg(2 seconds)

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
                "type": "generator",
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
                    "times": 100,
                    "delay": 2
                }
            } """).asInstanceOf[JObject]

            val props = CoralActorFactory.getProps(definition).get
            val generator = TestActorRef[GeneratorActor](props)
            val probe = TestProbe()
            generator.underlyingActor.emitTargets += probe.ref

            // Generates JNothing objects which are not received
            probe.expectNoMsg()
        }

        "Emit nothing on improperly structured generator function 'N'" in {
            val definition = parse( """ {
                "type": "generator",
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
                    "times": 100,
                    "delay": 2
                }
            } """).asInstanceOf[JObject]

            val props = CoralActorFactory.getProps(definition).get
            val generator = TestActorRef[GeneratorActor](props)
            val probe = TestProbe()
            generator.underlyingActor.emitTargets += probe.ref

            probe.expectNoMsg()
        }

        "Emit nothing on improperly structured generator function with a list" in {
            val definition = parse( """ {
                "type": "generator",
                "format": {
                    "field1": "N(100.25, 10.53)",
                    "field2": "[[''a', 'b,, 'c']",
                }, "timer": {
                    "rate": 10,
                    "times": 100,
                    "delay": 2
                }
            } """).asInstanceOf[JObject]

            val props = CoralActorFactory.getProps(definition).get
            val generator = TestActorRef[GeneratorActor](props)
            val probe = TestProbe()
            generator.underlyingActor.emitTargets += probe.ref

            probe.expectNoMsg()
        }
    }
}