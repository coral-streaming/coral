package io.coral.actors.transform

import akka.actor.Props
import io.coral.actors.CoralActor
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._


object ZscoreActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
		// from json actor definition
		// possible parameters server/client, url, etc
			by <- (json \ "params" \ "by").extractOpt[String]
			field <- (json \ "params" \ "field").extractOpt[String]
			score <- (json \ "params" \ "score").extractOpt[Double]
		} yield {
			(by, field, score)
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[ZscoreActor], json))
		// todo: take better care of exceptions and error handling
	}

}

// metrics actor example
class ZscoreActor(json: JObject) extends CoralActor {
	def jsonDef = json
	val (by, field, score) = ZscoreActor.getParams(jsonDef).get
	var outlier: Boolean = false
	def state = Map.empty

	def trigger = {
		json: JObject =>
			for {
			// from trigger data
				subpath <- getTriggerInputField[String](json \ by)
				value <- getTriggerInputField[Double](json \ field)

				// from other actors
				avg <- getCollectInputField[Double]("histogram", subpath, "avg")
				std <- getCollectInputField[Double]("histogram", subpath, "sd")

			//alternative syntax from other actors multiple fields
			//(avg,std) <- getActorField[Double](s"/user/events/histogram/$city", List("avg", "sd"))
			} yield {
				// compute (local variables & update state)
				val th = avg + score * std
				outlier = value > th
			}
	}

	def emit = {
		json: JObject =>

			outlier match {
				case true =>
					// produce emit my results (dataflow)
					// need to define some json schema, maybe that would help
					val result = ("outlier" -> outlier)

					// what about merging with input data?
					val js = render(result) merge json

					//logs the outlier
					log.warning(compact(js))

					//emit resulting json
					js

				case _ => JNothing
			}
	}
}