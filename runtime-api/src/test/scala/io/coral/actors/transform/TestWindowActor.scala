package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.json4s.JObject
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.json4s.native.JsonMethods._
import scala.concurrent.duration._

class TestWindowActor(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

    implicit val timeout = Timeout(1.seconds)
    val duration = timeout.duration

    def this() = this(ActorSystem("testSystem"))

    "A WindowActor" should {
        "Emit nothing on invalid settings" in {
            val constructor = parse(
                """{ "type": "window", "method":
                  |"count", "number": 10, "window": 2 }""".stripMargin).asInstanceOf[JObject]


        }
    }

    /*
    1) ("count", 10, 2) would mean that the actor emits 10 events at a time, but the two oldest are deleted
    and the two newest are added. This means the list has an overlap of items with the previous list.
    2) ("count", 10, 0) would mean that the actor emits a list of JSON objects after 10 incoming events,
    and the list is unique every time. This means that the list has no overlap with the previous one,
    unless duplicate events come in.
    3) ("time", 10, 0) would mean that the actor emits all events it has collected in the 10 past seconds
    and then resets. This means that the list has no overlap with the previous one, unless duplicate
    events come in.
    4) ("time", 10, 2), would mean that the actor emits all events it has collected in the last 10 seconds
    and every 2 seconds the window slides forward. This means that the events in the 8 seconds that
    overlap are duplicated.
     */
}