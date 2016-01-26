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
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.render
import io.coral.actors.{CollectDef, CoralActor}
import scala.concurrent.Future

object ZscoreActor {
	implicit val formats = org.json4s.DefaultFormats

	def collectAliases = List("count", "avg", "std")

	def getParams(json: JValue) = {
		for {
			field <- (json \ "params" \ "field").extractOpt[String]
			score <- (json \ "params" \ "score").extractOpt[Double]
			if (CollectDef.validCollectDef(json.asInstanceOf[JObject],
				collectAliases).isRight)
		} yield {
			(field, score)
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[ZscoreActor], json))
	}
}

class ZscoreActor(json: JObject) extends CoralActor(json) {
	val (field, score) = ZscoreActor.getParams(jsonDef).get

	override def trigger = { json =>
		val value = (json \ field).extract[Double]

		for {
			count <- collect[Int]("count")
			avg <- collect[Double]("avg")
			std <- collect[Double]("std")
			outlier <- isOutlier(count, avg, std, value)
		} yield {
			determineOutput(json, outlier)
		}
	}

	private def isOutlier(count: Long, avg: Double, std: Double, value: Double): Future[Boolean] = {
		Future {
			val th = avg + score * std
			// arbitrary count threshold to make sure there
			// is enough data to perform calculation on
			(value > th) & (count > 20)
		}
	}

	private def determineOutput(json: JObject, outlier: Boolean): Option[JValue] = {
		outlier match {
			case true =>
				// produce emit my results (dataflow)
				// need to define some json schema, maybe that would help
				val result = ("outlier" -> outlier)

				// what about merging with input data?
				val js = render(result) merge json

				//emit resulting json
				Some(js)
			case _ => Some(JNothing)
		}
	}
}