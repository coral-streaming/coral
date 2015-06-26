package io.coral.actors.transform

// scala
import scala.collection.immutable.SortedMap
import akka.actor.{ActorLogging, Props}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import io.coral.actors.{CoralActor, CoralActorFactory}
import io.coral.actors.Messages._
import scaldi.Injector

import akka.pattern.pipe

object GroupByActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      by <- (json \ "attributes" \ "group" \ "by").extractOpt[String]
    } yield {
      by
    }
  }

  def apply(json: JValue)(implicit injector: Injector): Option[Props] = {
    getParams(json).map(_ => Props(classOf[GroupByActor], json, injector))
  }
}

class GroupByActor(json: JObject)(implicit injector: Injector) extends CoralActor with ActorLogging {
  val Diff(_, _, jsonChildrenDef) = json diff JObject(("attributes", JObject(("group",   json \ "attributes" \ "group"))))
  val Diff(_, _, jsonDef)         = json diff JObject(("attributes", JObject(("timeout", json \ "attributes" \ "timeout"))))

  val by = GroupByActor.getParams(json).get

  def state = Map(("actors", render(children)))
  def emit  = emitNothing

  override def trigger = {
    json =>
      for {
        value <- getTriggerInputField[String](json \ by)
      } yield {

        // create if it does not exist
        val found = children.get(value) flatMap (id => actorRefFactory.child(id.toString))

        found match {
          case Some(actorRef) =>
            actorRef forward json

          case None =>
            val counter = askActor("/user/coral", GetCount()).mapTo[Long]

            counter onSuccess {
              case id =>
                val props = CoralActorFactory.getProps(jsonChildrenDef)
                props map { p =>
                  val actor = actorRefFactory.actorOf(p, s"$id")
                  children += (value -> id)
                  tellActor("/user/coral", RegisterActorPath(id, actor.path))
                  actor forward json
                }

            }
        }
      }
  }
}
