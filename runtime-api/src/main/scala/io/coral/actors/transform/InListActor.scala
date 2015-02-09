package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import io.coral.actors.{CoralActorFactory, CoralActor}
import io.coral.actors.Messages.{RegisterActorPath, GetCount}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.immutable.SortedMap

object InListActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		// TODO: Change this
		for {
			by <- (json \ "group" \ "by").extractOpt[String]
		} yield {
			by
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[InListActor], json))
	}
}

/* RestActor sends requests to InListActor */
class InListActor(json: JObject) extends CoralActor with ActorLogging {
	def jsonDef = json
	var childLists = SortedMap.empty[String, Long]
	def state = Map()
	//def state = Map(("actors", render(childLists)))
	def emit = doNotEmit
	def trigger = ???
}