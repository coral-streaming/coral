package io.coral.actors.transform

import org.json4s._
import io.coral.actors.CoralActor

object RestActor {
	//akka actors props
	import akka.actor.Props

	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
		// from json actor definition
		// possible parameters server/client, url, etc
			t <- (json \ "type").extractOpt[String]
		} yield {
			t
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[RestActor], json))
		// todo: take better care of exceptions and error handling
	}
}

class RestActor(json: JObject) extends CoralActor {
	def jsonDef = json
	def state = Map.empty
	def trigger = noProcess
	def emit = passThroughEmit
}