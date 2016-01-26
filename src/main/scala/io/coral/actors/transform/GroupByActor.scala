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
import org.json4s.jackson.JsonMethods._
import io.coral.actors.{NoEmitTrigger, CoralActor, CoralActorFactory}
import scaldi.Injector
import akka.pattern.pipe

object GroupByActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			by <- (json \ "group" \ "by").extractOpt[String]
		} yield {
			by
		}
	}

	def apply(json: JValue)(implicit injector: Injector): Option[Props] = {
		getParams(json).map(_ => Props(classOf[GroupByActor], json, injector))
	}
}

class GroupByActor(json: JObject)(implicit injector: Injector)
	extends CoralActor(json)
	with NoEmitTrigger
	with ActorLogging {
	val Diff(_, _, jsonChildrenDef) = json diff JObject(("group", json \ "group"))
	val Diff(_, _, jsonDefinition) = json diff JObject(("timeout", json \ "timeout"))
	val by = GroupByActor.getParams(json).get
	override def jsonDef = jsonDefinition.asInstanceOf[JObject]
	override def state = Map(("actors", render(children)))

	override def noEmitTrigger(json: JObject) = {
		for {
			value <- (json \ by).extractOpt[String]
		} yield {
			val found = children.get(value) flatMap (id => actorRefFactory.child(id.toString))

			found match {
				case Some(actorRef) =>
					// We found the child, send it the original message
					actorRef forward json
				case None =>
					// Not found, create a new child
					val props = CoralActorFactory.getProps(jsonChildrenDef)
					props map { p =>
						val actor = actorRefFactory.actorOf(p, s"blabla")
						children += (value -> 1L)
						actor forward json
					}
			}
		}
	}
}