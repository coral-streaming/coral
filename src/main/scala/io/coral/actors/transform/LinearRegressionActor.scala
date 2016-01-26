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
import org.json4s.jackson.JsonMethods.{render, pretty}
import io.coral.actors.{SimpleEmitTrigger, CoralActor}

object LinearRegressionActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			intercept <- (json \ "params" \ "intercept").extractOpt[Double]
			weights <- (json \ "params" \ "weights").extractOpt[Map[String, Double]]
		} yield {
			val outcome = (json \ "params" \ "outcome").extractOpt[String]
			(intercept, weights, outcome)
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[LinearRegressionActor], json))
	}
}

class LinearRegressionActor(json: JObject)
	extends CoralActor(json)
	with ActorLogging
	with SimpleEmitTrigger {
	val (intercept, weights, outcome) = LinearRegressionActor.getParams(json).get

	override def simpleEmitTrigger(json: JObject): Option[JValue] = {
		val inputVector = weights.keys.map(key => {
			(json \ key).extractOpt[Double] match {
				case Some(value) => Some(value)
				case None => None
			}
		}).toVector

		if (inputVector.exists(!_.isDefined)) {
			None
		} else {
			val result = intercept + (inputVector.flatten zip weights.values).map(x => x._1 * x._2).sum
			val name = if (outcome.isDefined) outcome.get else "score"
			Some(render(name -> result) merge json)
		}
	}
}