/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.actors.transform

import akka.actor.{ActorLogging, Props}
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods.render
import io.coral.actors.{SimpleTimer, NoEmitTrigger, CoralActor}
import io.coral.lib.SummaryStatistics

import scala.language.implicitConversions

object StatsActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			field <- (json \ "params" \ "field").extractOpt[String]
		} yield {
			field
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[StatsActor], json))
	}
}

class StatsActor(json: JObject)
	extends CoralActor(json)
	with ActorLogging
	with NoEmitTrigger
	with SimpleTimer {
	implicit def double2jvalue(x: Double): JValue = if (x.isNaN) JNull else JDouble(x)

	val field = StatsActor.getParams(json).get
	val statistics = SummaryStatistics.mutable

	override def simpleTimer = {
		statistics.reset()
		Some(JNothing)
	}

	override def state = Map(
		("count", render(statistics.count)),
		("avg", render(statistics.average)),
		("sd", render(statistics.populationSd)),
		("min", render(statistics.min)),
		("max", render(statistics.max))
	)

	override def noEmitTrigger(json: JObject) = {
		for {
			value <- (json \ field).extractOpt[Double]
		} yield {
			statistics.append(value)
		}
	}
}