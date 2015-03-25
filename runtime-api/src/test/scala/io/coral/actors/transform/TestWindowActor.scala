package io.coral.actors.transform

import akka.actor.{Actor, ActorInitializationException, Props, ActorSystem}
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.actors.Messages._
import io.coral.actors.transform.WindowActor
import org.json4s.JsonAST.{JValue, JNothing, JInt, JString, JObject}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.json4s.native.JsonMethods._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

class TestWindowActor(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

    implicit val timeout = Timeout(100.seconds)
    val duration = timeout.duration

    def this() = this(ActorSystem("testSystem"))

    "A WindowActor" should {
        "Do not create a new actor with an improper definition" in {
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"invalid", "number": 3, "sliding": 1 }}""".stripMargin).asInstanceOf[JObject]

            intercept[IllegalArgumentException] {
                val windowActor = WindowActor(constructor)
                assert(windowActor == None)
            }
        }

        "Do not create a new actor with negative sliding window" in {
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"count", "number": 3, "sliding": -3 }}""".stripMargin).asInstanceOf[JObject]

            intercept[IllegalArgumentException] {
                val windowActor = WindowActor(constructor)
                assert(windowActor == None)
            }
        }

        "Do not create a new actor with a negative number" in {
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"count", "number": -3, "sliding": 3 }}""".stripMargin).asInstanceOf[JObject]

            intercept[IllegalArgumentException] {
                val windowActor = WindowActor(constructor)
                assert(windowActor == None)
            }
        }

        "Do not create a new actor with a floating point number parameter" in {
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"count", "number": 4.42, "sliding": 3 }}""".stripMargin).asInstanceOf[JObject]

            intercept[IllegalArgumentException] {
                val windowActor = WindowActor(constructor)
                assert(windowActor == None)
            }
        }

        "Do not create a new actor with a floating point sliding parameter" in {
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"count", "number": 4, "sliding": 3.42 }}""".stripMargin).asInstanceOf[JObject]

            intercept[IllegalArgumentException] {
                def windowActor = WindowActor(constructor)
                assert(windowActor == None)
            }
        }

        "Automatically set a sliding window with only number definition" in {
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"count", "number": 3 }}""".stripMargin).asInstanceOf[JObject]

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
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"count", "number": 3, "sliding": 3 } }""".stripMargin).asInstanceOf[JObject]

            val windowActor = system.actorOf(Props(new WindowActor(constructor)))

            for (i <- 1 to 4) {
                val json = parse(s"""{ "name": "object$i" } """)
                windowActor ! Trigger(json.asInstanceOf[JObject])
                Thread.sleep(300)
            }

            var emitted = Await.result(windowActor.ask(Emit()), duration)
            var expected = parse(
                """{ "data": [ { "name" : "object1" },
                     { "name" : "object2" },
                     { "name" : "object3" } ] } """)
            assert(emitted == expected)

            // Queue contains only one item so expect no response
            emitted = Await.result(windowActor.ask(Emit()), duration)
            assert(emitted == JNothing)

            var json = parse(s"""{ "name": "object5" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])
            emitted = Await.result(windowActor.ask(Emit()), duration)
            assert(emitted == JNothing)

            json = parse(s"""{ "name": "object6" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])
            emitted = Await.result(windowActor.ask(Emit()), duration)
            expected = parse(
                """{ "data": [ { "name" : "object4" },
                     { "name" : "object5" },
                     { "name" : "object6" } ] }""")
            assert(emitted == expected)

            json = parse(s"""{ "name": "object7" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])
            json = parse(s"""{ "name": "object8" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])
            json = parse(s"""{ "name": "object9" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])

            emitted = Await.result(windowActor.ask(Emit()), duration)
            expected = parse(
                """{ "data": [ { "name" : "object7" },
                     { "name" : "object8" },
                     { "name" : "object9" } ] } """)
            assert(emitted == expected)
        }

        "Properly perform ('count', 3, 1)" in {
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"count", "number": 3, "sliding": 1 } }""".stripMargin).asInstanceOf[JObject]
            val windowActor = system.actorOf(Props(new WindowActor(constructor)))

            val method = Await.result(windowActor.ask(GetField("method")), duration)
            assert(method == JString("count"))

            val number = Await.result(windowActor.ask(GetField("number")), duration)
            assert(number == JInt(3))

            val sliding = Await.result(windowActor.ask(GetField("sliding")), duration)
            assert(sliding == JInt(1))

            for (i <- 1 to 3) {
                val json = parse(s"""{ "name": "object$i" } """)
                windowActor ! Trigger(json.asInstanceOf[JObject])
                Thread.sleep(300)
            }

            var emitted = Await.result(windowActor.ask(Emit()), duration)
            var expected = parse(
                """{ "data": [ { "name" : "object1" },
                     { "name" : "object2" },
                     { "name" : "object3" } ] }""")
            assert(emitted == expected)

            // Queue contains only one item so expect no response
            emitted = Await.result(windowActor.ask(Emit()), duration)
            assert(emitted == JNothing)

            var json = parse(s"""{ "name": "object4" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])
            emitted = Await.result(windowActor.ask(Emit()), duration)
            expected = parse(
                """{ "data": [ { "name" : "object2" },
                     { "name" : "object3" },
                     { "name" : "object4" } ] }""")
            assert(emitted == expected)

            json = parse(s"""{ "name": "object5" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])
            emitted = Await.result(windowActor.ask(Emit()), duration)
            expected = parse(
                """{ "data": [ { "name" : "object3" },
                     { "name" : "object4" },
                     { "name" : "object5" } ] }""")
            assert(emitted == expected)
        }

        "Properly perform ('count', 3, 2)" in {
            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"count", "number": 3, "sliding": 2 } }""".stripMargin).asInstanceOf[JObject]
            val windowActor = system.actorOf(Props(new WindowActor(constructor)))

            for (i <- 1 to 3) {
                val json = parse(s"""{ "name": "object$i" } """)
                windowActor ! Trigger(json.asInstanceOf[JObject])
            }

            var emitted = Await.result(windowActor.ask(Emit()), duration)
            var expected = parse(
                """{ "data": [ { "name" : "object1" },
                     { "name" : "object2" },
                     { "name" : "object3" } ] }""")
            assert(emitted == expected)

            // Queue contains only one item so expect no response
            emitted = Await.result(windowActor.ask(Emit()), duration)
            assert(emitted == JNothing)

            var json = parse(s"""{ "name": "object4" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])

            emitted = Await.result(windowActor.ask(Emit()), duration)
            assert(emitted == JNothing)

            json = parse(s"""{ "name": "object5" } """)
            windowActor ! Trigger(json.asInstanceOf[JObject])
            emitted = Await.result(windowActor.ask(Emit()), duration)
            expected = parse(
                """{ "data": [ { "name" : "object3" },
                     { "name" : "object4" },
                     { "name" : "object5" } ] }""")
            assert(emitted == expected)
        }

        "Properly perform ('time', 1, 1)" in {
            // This means: emit everything you have
            // collected in the last second without overlap

            val constructor = parse(
                """{ "type": "window", "params" : { "method":
                  |"time", "number": 1000, "sliding": 1000 }}""".stripMargin).asInstanceOf[JObject]
            val windowActor = system.actorOf(Props(new WindowActor(constructor)))

            val probe = TestProbe()
            windowActor ! RegisterActor(probe.ref)

            for (i <- 1 to 10) {
                val json = parse(s"""{ "name": "object$i" } """)
                windowActor ! Trigger(json.asInstanceOf[JObject])
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
    }
}