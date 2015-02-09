package io.coral.actors.transform

import akka.actor.Props
import io.coral.actors.CoralActor
import io.coral.api.JsonConversions._
import org.json4s._
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse}
import scala.concurrent.Future
import scala.util.{Failure, Success}

object HttpClientActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
		// from json actor definition
		// possible parameters server/client, url, etc
			url <- (json \ "params" \ "url").extractOpt[String]
		} yield {
			(url)
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[HttpClientActor], json))
		// todo: take better care of exceptions and error handling
	}
}

class HttpClientActor(json: JObject) extends CoralActor {
	def jsonDef = json
	val (url) = HttpClientActor.getParams(jsonDef).get
	def state = Map.empty

	def trigger = {
		json: JObject =>
			for {
			// from trigger data
				outlier <- getTriggerInputField[Boolean](json \ "outlier")
			} yield {
				// post
				val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
				val response = pipeline(Post(url, json))

				response onComplete {
					case Success(resp) => log.warning("successfully posted")
					case Failure(error) => log.warning("Failure: " + error)
				}
			}
	}

	def emit = doNotEmit
}