package io.coral.actors.transform

import akka.actor.Props
import io.coral.actors.CoralActor
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

object HistogramActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
		// from trigger data
			field <- (json \ "params" \ "field").extractOpt[String]
		} yield {
			field
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[HistogramActor], json))
		// todo: take better care of exceptions and error handling
	}
}

class HistogramActor(json: JObject) extends CoralActor {
	def jsonDef = json
	val field = HistogramActor.getParams(json).get

	var count = 0L
	var avg = 0.0
	var sd = 0.0
	var `var` = 0.0

	def state = Map(
		("count", render(count)),
		("avg", render(avg)),
		("sd", render(Math.sqrt(`var`))),
		("var", render(`var`))
	)

	var avg_1 = 0.0

	def trigger = {
		json: JObject =>
			for {
				// from trigger data
				value <- getTriggerInputField[Double](json \ field)
			} yield {
				// compute (local variables & update state)
				count match {
					case 0 =>
						count = 1
						avg_1 = value
						avg = value
					case _ =>
						count += 1
						avg_1 = avg
						avg = avg_1 * (count - 1) / count + value / count
				}

				// descriptive variance
				count match {
					case 0 =>
						`var` = 0.0
					case 1 =>
						`var` = (value - avg_1) * (value - avg_1)
					case _ =>
						`var` = ((count - 2) * `var` + (count - 1) * (avg - avg) * (avg_1 - avg)
							+ (value - avg) * (value - avg)) / (count - 1)
				}
			}
	}

	def emit = doNotEmit
}