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
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.render
import io.coral.actors.{SimpleEmitTrigger, CoralActor}

object ThresholdActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			key <- (json \ "params" \ "key").extractOpt[String]
			threshold <- (json \ "params" \ "threshold").extractOpt[Double]
		} yield (key, threshold)
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[ThresholdActor], json))
	}
}

class ThresholdActor(json: JObject) extends CoralActor(json) with ActorLogging with SimpleEmitTrigger {
	val (key, threshold) = ThresholdActor.getParams(json).get

	override def simpleEmitTrigger(json: JObject): Option[JValue] = {
		for {
			value <- (json \ key).extractOpt[Double]
		} yield {
			value >= threshold match {
				case true => json
				case false => JNothing
			}
		}
	}
}