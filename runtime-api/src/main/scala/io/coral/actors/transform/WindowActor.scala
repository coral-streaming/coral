package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import com.datastax.driver.core.{ResultSet, Session, Cluster}
import io.coral.actors.CoralActor
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.collection.mutable.Queue

import scala.concurrent.Future
import scala.util.Try
import scalaz.OptionT

/*
    method = "count" / "time"
             for instance: "count"
    number = how many events / how many seconds
             for instance: 10
    window = how many events / how many seconds slides each time
             if not given, no sliding window is used
             for instance: 2

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

    Example:

    count: 1 2 3 4 5 6 7 8 9 10 11 12
    event: A B C D E F G H I J  K  L

    1) The actor waits until it has 10 events, and then emits (A B C D E F G H I J).
    After two new events come in, it emits (C D E F G H I J K L) and then (E F G H I J K L . .).
    2) It first emits (A B C D E F G H I J) and then emits (K L . . . . . . . .)
    3) Depending on when the events come in, it emits all events collected in the last 10 seconds
    and then waits 10 seconds before collecting again, so there is no overlap.
    4) It emits all events collected in the last 10 seconds, but does this again every 2 seconds.

    If the number of required events are not collected during the lifetime of this actor, nothing will ever
    be emitted, it will simply wait forever.
*/
object WindowActor {
    implicit val formats = org.json4s.DefaultFormats

    def getParams(json: JValue) = {
        for {
            method <- (json \ "method").extractOpt[String]
            number <- (json \ "number").extractOpt[Int]
            window  <- (json \ "window").extractOpt[Int]
        } yield {
            (method, number, window)
        }
    }

    def apply(json: JValue): Option[Props] = {
        getParams(json).map(_ => Props(classOf[WindowActor], json))
    }
}

class WindowActor(json: JObject) extends CoralActor with ActorLogging {
    def jsonDef = json

    var (method, number, window) = WindowActor.getParams(json).get

    // The list of items is a tuple with adding time
    val items = Queue.empty[(Long, JObject)]
    var toEmit: List[JObject] = _

    override def preStart() {

    }

    def state = Map(
        // TODO: Why does this give a compilation error?
        ("method", render(JString(method))),
        ("number", render(JInt(number))),
        ("window", render(JInt(window)))
    )

    // TODO: When is the timer fired? How to set it?
    def timer = {
        dequeueItems(method, number, window)
        JNothing
    }

    def trigger = {
        json: JObject =>
            items.enqueue((System.currentTimeMillis, json))
            dequeueItems(method, number, window)
            OptionT.some(Future.successful({}))
    }

    def dequeueItems(method: String, count: Int, window: Int): List[JObject] = {
        /*
        if (method == "time") {
            // Only run timer in time-based mode
            val currentTime = System.currentTimeMillis
            //
            items.dequeueFirst(x => x._1 < currentTime)
        }

        // Enqueue the object with the current time
        items.enqueue((System.currentTimeMillis, json))

        // If we have enough objects, then find out which to emit
        if (items.size >= number) {
            toEmit = findObjectsToEmit(items, number, window)
        }

        //items.take(number).map(e => e._2).toList

        // Clear the objects that were emitted from the original list
        */
        null
    }

    def emit = {
        json: JObject =>
            // render(toEmit)
            toEmit = List.empty[JObject]
            JNothing
    }
}