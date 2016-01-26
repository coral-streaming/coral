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
import io.coral.lib.JsonTemplate
import org.json4s._
import org.json4s.jackson.JsonMethods.render
import io.coral.actors.{SimpleEmitTrigger, CoralActor}
import io.coral.actors.transform.LookupActor._

object LookupActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			lookup <- (json \ "params" \ "lookup").extractOpt[Map[String, JObject]]
			key <- (json \ "params" \ "key").extractOpt[String]
			function <- determineFunction(json)
			matchType <- determineMatch(json)
		} yield {
			val defaultValue = determineDefaultValue(json)
			(key, lookup, function, matchType, defaultValue)
		}
	}

	private def determineDefaultValue(json: JValue): Option[JsonTemplate] = {
		val defaultValue = (json \ "params" \ "default").extractOpt[JObject]
		defaultValue.map(JsonTemplate(_))
	}

	private def determineMatch(json: JValue): Option[MatchType] = {
		val matchType = (json \ "params" \ "match").extractOpt[String]
		matchType match {
			case Some("exact") => Some(Exact)
			case Some("startswith") => Some(StartsWith)
			case None => Some(Exact)
			case _ => None
		}
	}

	private def determineFunction(json: JValue): Option[LookupFunction] = {
		val function = (json \ "params" \ "function").extractOpt[String]
		function match {
			case Some("enrich") => Some(Enrich)
			case Some("filter") => Some(Filter)
			case Some("check") => Some(Check)
			case _ => None
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[LookupActor], json))
	}

	abstract sealed class LookupFunction
	object Enrich extends LookupFunction
	object Filter extends LookupFunction
	object Check extends LookupFunction

	abstract sealed class MatchType
	object Exact extends MatchType
	object StartsWith extends MatchType
}

class LookupActor(json: JObject)
	extends CoralActor(json)
	with ActorLogging
	with SimpleEmitTrigger {

	val (key, lookup, function, matchType, defaultValue) = LookupActor.getParams(json).get

	override def simpleEmitTrigger(json: JObject): Option[JValue] = {
		for {
			value <- (json \ key).extractOpt[String]
		} yield {
			val lookupObject = determineLookup(value)

			function match {
				case Enrich => json merge render(lookupObject getOrElse determineDefaultValue(json))
				case Filter => lookupObject map (_ => json) getOrElse determineDefaultValue(json)
				case Check => render(lookupObject getOrElse determineDefaultValue(json))
			}
		}
	}

	private def determineDefaultValue(json: JObject): JValue = {
		defaultValue match {
			case Some(template) => template.interpret(json)
			case None => JNothing
		}
	}

	private def determineLookup(value: String): Option[JObject] = {
		matchType match {
			case Exact => exactMatch(value)
			case StartsWith => startsWithMatch(value)
		}
	}

	private def exactMatch(value: String): Option[JObject] = {
		lookup.get(value)
	}

	private def startsWithMatch(value: String): Option[JObject] = {
		lookup.find { case (key, _) => value.startsWith(key) }.map(_._2)
	}
}