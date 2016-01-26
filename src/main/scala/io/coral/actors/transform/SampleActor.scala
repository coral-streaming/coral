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
import io.coral.actors.{SimpleEmitTrigger, CoralActor}
import io.coral.lib.Random
import org.json4s.JsonAST.JNothing
import org.json4s.{JObject, JValue}

object SampleActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			fraction <- (json \ "params" \ "fraction").extractOpt[Double]
		} yield {
			fraction
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[SampleActor], json, Random))
	}
}

class SampleActor(json: JObject, random: Random)
	extends CoralActor(json)
	with SimpleEmitTrigger {
	val fraction: Double = SampleActor.getParams(json).get
	var randomStream: Stream[Boolean] = random.binomial(fraction)

	def next(): Boolean = {
		val value = randomStream.head
		randomStream = randomStream.tail
		value
	}

	override def simpleEmitTrigger(json: JObject): Option[JValue] = {
		next() match {
			case false => Some(JNothing)
			case true => Some(json)
		}
	}
}