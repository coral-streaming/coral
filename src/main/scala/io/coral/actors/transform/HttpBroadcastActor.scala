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
import io.coral.actors.{SimpleEmitTrigger, CoralActor}

object HttpBroadcastActor {
	implicit val formats = org.json4s.DefaultFormats

	def apply(json: JValue): Option[Props] = {
		Some(Props(classOf[HttpBroadcastActor], json))
	}
}

class HttpBroadcastActor(json: JObject)
	extends CoralActor(json)
	with ActorLogging
	with SimpleEmitTrigger {

	override def simpleEmitTrigger(json: JObject): Option[JValue] = {
		Some(json)
	}
}