package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import com.datastax.driver.core.{ResultSet, Session, Cluster}
import io.coral.actors.CoralActor
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.collection.mutable.Queue
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try
import scalaz.OptionT
import akka.actor.Props
import com.datastax.driver.core.{DataType, ResultSet, Session, Cluster}
import org.json4s.JValue
import org.json4s.JsonAST.JValue
import scala.concurrent.{Promise, Future}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import io.coral.actors.CoralActor
import scalaz.{OptionT, Monad}
import scalaz.OptionT._


/**
 * WindowActor class collects input events and saves them until a certain
 * number of events has been reached or a certain time has passed.
 *
 * The parameters to set are the following:
 *   method = "count" / "time"
 *            for instance: "count"
 *   number = how many events / how many milliseconds
 *            for instance: 10 / 10000
 *   sliding = how many events / how many milliseconds slides each time
 *            if not given, no sliding window is used
 *            for instance: 2 / 2000
 *
 * 1) ("count", 10, 2) would mean that the actor emits 10 events at a time, but the two oldest are deleted
 * and the two newest are added. This means the list has an overlap of items with the previous list.
 * 2) ("count", 10, 10) would mean that the actor emits a list of JSON objects after 10 incoming events,
 * and the list is unique every time. This means that the list has no overlap with the previous one,
 * unless duplicate events come in.
 * 3) ("time", 10000, 10000) would mean that the actor emits all events it has collected in the 10 past seconds
 * and then resets. This means that the list has no overlap with the previous one, unless duplicate events
 * come in.
 * 4) ("time", 10000, 2000), would mean that the actor emits all events it has collected in the last 10 seconds
 * and every 2 seconds the window slides forward. This means that the events in the 8 seconds that
 * overlap are duplicated.
 *
 * Example:
 * order: (latest) 12 11 10 9 8 7 6 5 4 3 2 1 (earliest)
 * event:           L  K  J I H G F E D C B A
 *
 * 1) The actor waits until it has 10 events, and then emits (A B C D E G H I J K).
 * After two new events come in, it emits (C D E F G H I J K L) and then (E F G H I J K L . .).
 * 2) It first emits (A B C D E F G H I J) and then emits (K L . . . . . . . .)
 * 3) Depending on when the events come in, it emits all events collected in the last 10 seconds
 * and then waits 10 seconds before collecting again, so there is no overlap.
 * 4) It emits all events collected in the last 10 seconds, but does this again every 2 seconds.
 *
 * If the number of required events are not collected during the lifetime of this actor, nothing
 * will ever be emitted, it will simply wait forever.
 */
object WindowActor {
    implicit val formats = org.json4s.DefaultFormats

    def getParams(json: JValue) = {
        for {
            method <- getMethod(json)
            number <- getNumber(json, method)
            // When no sliding window is given, no overlap is assumed
            sliding <- getSliding(json, number, method)
        } yield {
            (method, number, sliding)
        }
    }

    def getMethod(json: JValue): Option[String] = {
        val method = (json \ "attributes" \ "params" \ "method")

        val value: String = method match {
            case JString(s) if List("count", "time").contains(s) => s
            case _ => throw new IllegalArgumentException("method")
        }

        Some(value)
    }

    def getNumber(json: JValue, method: String): Option[Int] = {
        val number = (json \ "attributes" \ "params" \ "number")
        val value: Int = number match {
            case JDouble(_) | JNothing => throw new IllegalArgumentException("number")
            case JInt(i) =>
                if (i.toInt <= 0) {
                    throw new IllegalArgumentException("number")
                } else {
                    i.toInt
                }
            case _ => throw new IllegalArgumentException("number")
        }

        Some(value)
    }

    def getSliding(json: JValue, number: Int, method: String): Option[Int] = {
        val sliding = (json \ "attributes" \ "params" \ "sliding")

        val value: Int = sliding match {
            case JDouble(_) => throw new IllegalArgumentException("sliding")
            case JNothing => number
            case JInt(i) =>
                if (i.toInt <= 0) {
                    throw new IllegalArgumentException("number")
                } else {
                    i.toInt
                }
            case _ => throw new IllegalArgumentException("number")
        }

        Some(value)
    }

    def apply(json: JValue): Option[Props] = {
        getParams(json).map(_ => Props(classOf[WindowActor], json))
    }
}

class WindowActor(json: JObject) extends CoralActor with ActorLogging {
    def jsonDef = json

    var (method, number, sliding) = WindowActor.getParams(json).get

    // The list of items is a tuple with adding time
    val items = Queue.empty[(Long, JObject)]
    var toEmit = List.empty[JObject]

    // A counter to keep track of the sliding window position
    var counter: Int = 0
    var startTime: Long = 0

    override def preStart() {
        // Start the timer, if any
        if (method == "time") {
            // The sliding interval is the interval at
            // which something actually has to happen
            startTime = System.currentTimeMillis()

            actorRefFactory.system.scheduler.schedule(sliding millis, sliding millis) {
                performTimeWindow()
                transmit(emit(JObject()))
            }
        }
    }

    override def state = Map(
        ("method", render(JString(method))),
        ("number", render(JInt(number))),
        ("sliding", render(JInt(sliding)))
    )

    override def trigger = {
        json: JObject =>
            items.enqueue((System.currentTimeMillis, json))

            // Only process the "count" method here,
            // "time" is handled by the timer
            if (method == "count") {
                performCountWindow()
            }

            OptionT.some(Future.successful({}))
    }

    /**
     * This function is executed in "count" mode.
     * The items that need to be emitted are checked.
     */
    def performCountWindow() {
        // We must have at least <number> items
        if (items.size >= number) {
            if (sliding == number) {
                // No sliding window, clear items and start over
                toEmit = items.map(x => x._2).toList
                items.clear()
            } else {
                // We have a sliding window
                counter += 1

                // Also emit when there are <number> items in the list
                if (counter == sliding || items.size == number) {
                    // It is time to move the sliding window
                    toEmit = items.take(number).map(x => x._2).toList
                    counter = 0

                    for (i <- 0 until sliding) {
                        // Not interested in the actual dequeued value
                        items.dequeue()
                    }
                }
            }
        }
    }

    /**
     * This function is executed in "time" mode on every sliding interval.
     * For instance, if sliding = 2 (seconds), this means that
     * this function will be called every two seconds to see
     * what objects need to be emitted.
     */
    def performTimeWindow() {
        if (sliding == number) {
            // No sliding window or overlap
            toEmit = items.map(x => x._2).toList
            items.clear()
        } else {
            // Dequeue and delete all items that are not
            // in the current time window any more
            val currentTime = System.currentTimeMillis

            // Dismissed when outside of the window
            items.dequeueAll(i => {
                val time = i._1
                time < (currentTime - number)
            })

            if (currentTime - startTime >= number) {
                toEmit = items.map(x => x._2).toList
                // No items.clear() here!
            }
        }
    }

    override def emit = {
        json: JObject =>
            if (method == "count" && toEmit.length < number) {
                JNothing
            } else if (method == "time" && toEmit.length == 0) {
                JNothing
            } else {
                val result = ("data" -> JArray(toEmit))

                // Reset the toEmit list
                toEmit = List.empty[JObject]

                result
            }
    }
}