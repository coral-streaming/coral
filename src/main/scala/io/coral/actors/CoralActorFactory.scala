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

package io.coral.actors

import org.json4s._
import io.coral.actors.transform.GroupByActor
import akka.actor.Props
import scaldi.Injectable._
import scaldi.Injector

object CoralActorFactory {
	def getProps(json: JValue)(implicit injector: Injector) = {
		val actorPropFactories = inject [List[ActorPropFactory]] (by default List())
		implicit val formats = org.json4s.DefaultFormats

		// check for grouping, if so generate a group actor and move on ...
		// otherwise, generate the proper actor
		val groupByProps = (json \ "group" \ "by").extractOpt[String] match {
			case Some(x) => GroupByActor(json)
			case None => None
		}

		val actorProps = for {
			actorType <- (json \ "type").extractOpt[String]
			props <- getActorProps(actorType, json, actorPropFactories)
		} yield props

		groupByProps orElse actorProps
	}

	private def getActorProps(actorType: String, json: JValue,
							  actorPropFactories: List[ActorPropFactory]): Option[Props] = {
		actorPropFactories match {
			case head :: tail => {
				head.getProps(actorType, json) orElse getActorProps(actorType, json, tail)
			}
			case Nil => None
		}
	}
}