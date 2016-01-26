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
import org.json4s._

object FilterActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue): Option[List[Filter]] = {
		for {
			filterDefs <- (json \ "params" \ "filters").extractOpt[List[JObject]]
			filters = filterDefs.map(createFilter).flatten
			if (filters.size == filterDefs.size && filters.size > 0)
		} yield {
			filters
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[FilterActor], json))
	}

	private def createFilter(json: JObject): Option[Filter] = {
		for {
			filterType <- (json \ "type").extractOpt[String].flatMap(filterType)
			function <- (json \ "function").extractOpt[String].flatMap(filterFunction)
			field <- (json \ "field").extractOpt[String]
			param <- (json \ "param").extractOpt[String]
		} yield (Filter(filterType, function, field, param))
	}

	private def filterType(filterType: String): Option[FilterType] = {
		filterType match {
			case "startswith" => Some(StartsWith)
			case _ => None
		}
	}

	private def filterFunction(filterFunction: String): Option[FilterFunction] = {
		filterFunction match {
			case "exclude" => Some(Exclude)
			case "include" => Some(Include)
			case _ => None
		}
	}

	case class Filter(filterType: FilterType, function: FilterFunction, field: String, param: String) {
		implicit val formats = org.json4s.DefaultFormats

		def filter(json: JObject): Boolean = {
			val applied = filterType match {
				case StartsWith => startsWith(json, field, param)
			}

			function match {
				case Exclude => !applied
				case Include => applied
			}
		}

		def startsWith(json: JObject, field: String, param: String): Boolean = {
			(json \ field).extractOpt[String].map(_.startsWith(param)) getOrElse false
		}
	}

	abstract sealed class FilterType
	object StartsWith extends FilterType

	abstract sealed class FilterFunction
	object Exclude extends FilterFunction
	object Include extends FilterFunction
}

class FilterActor(json: JObject)
	extends CoralActor(json)
	with SimpleEmitTrigger {

	val filters = FilterActor.getParams(json).get

	override def simpleEmitTrigger(json: JObject): Option[JValue] = {
		if (filters.forall(_.filter(json))) {
			Some(json)
		} else {
			Some(JNothing)
		}
	}
}