package io.coral.actors.transform

// akka
import akka.actor.Props

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// coral
import io.coral.actors.CoralActor

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

	var min = 0.0
	var max = 0.0

	def state = Map(
		("count", render(count)),
		("avg", render(avg)),
		("sd", render(Math.sqrt(`var`))),
		("min", render(min)),
		("max", render(max))
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
						min = value
						max = value
					case _ =>
						count += 1
						avg_1 = avg
						avg = avg_1 * (count - 1) / count + value / count
						min = if (value < min) value else min
						max = if (value > max) value else max
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