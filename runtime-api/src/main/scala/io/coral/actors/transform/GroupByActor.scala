package io.coral.actors.transform

// scala

import scala.collection.immutable.{HashMap, SortedSet, SortedMap}
import scala.concurrent.Future
import scala.concurrent.duration._

// akka
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// scalaz monad transformers
import scalaz.{OptionT, Monad}
import scalaz.OptionT._

// First attempt to describe a user DSL for collecting values
import scala.reflect.{ClassTag, Manifest}

// Inter-actor messaging
import io.coral.actors.Messages._
import io.coral.actors._

object GroupByActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      by <- (json \ "group" \ "by").extractOpt[String]
    } yield {
      by
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[GroupByActor], json))
  }
}

class GroupByActor(json: JObject) extends CoralActor with ActorLogging {
  def jsonDef = json
  val by = GroupByActor.getParams(json).get
  val Diff(_, _, jsonChildrenDef) = jsonDef diff JObject(("group", json \ "group"))
  var actors = SortedMap.empty[String, Long]
  def state = Map(("actors", render(actors)))
  def emit = doNotEmit
  def timer = notSet

  def trigger = {
    json: JObject =>
      for {
        value <- getTriggerInputField[String](json \ by)
        count <- getActorResponse[Long]("/user/coral", GetCount())
      } yield {
        // create if it does not exist
        actorRefFactory.child(value) match {
          case Some(actorRef) =>
            actorRef
          case None =>
            val props = CoralActorFactory.getProps(jsonChildrenDef)

            val actorOpt = props map { p =>
              val actor = actorRefFactory.actorOf(p, s"$value")
              actors += (value -> count)
              tellActor("/user/coral", RegisterActorPath(count, actor.path))
              actor
            }

            actorOpt.get
        }
      } ! json
  }
}
