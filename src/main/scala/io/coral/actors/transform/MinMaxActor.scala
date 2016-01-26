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

import akka.actor.Props
import io.coral.actors.CoralActor
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.Future

object MinMaxActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			min <- (json \ "params" \ "min").extractOpt[Double]
			max <- (json \ "params" \ "max").extractOpt[Double]
			field <- (json \ "params" \ "field").extractOpt[String]
			if (min < max)
		} yield {
			(min, max, field)
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[MinMaxActor], json))
	}
}

class MinMaxActor(json: JObject) extends CoralActor(json) {
	val (min, max, field) = MinMaxActor.getParams(json).get

	override def trigger = {
		json: JObject => {
			Future {
				val value = (json \ field).extractOpt[Double]

				value match {
					case None =>
						Some(json)
					case Some(v) =>
						val capped = if (v < min) min
						else if (v > max) max
						else v

						val result = json mapField {
							case (f, JInt(_)) if f == field =>
								(f, JDouble(capped))
							case (f, JDouble(_)) if f == field =>
								(f, JDouble(capped))
							case other =>
								other
						}

						Some(result)
				}
			}
		}
	}
}