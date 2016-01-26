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

import scala.concurrent.Future
import org.json4s.JsonAST.{JObject, JValue}

trait Trigger {
	type TriggerType = JObject => Future[Option[JValue]]

	def trigger: TriggerType
}

trait NoEmitTrigger extends Trigger {
	override def trigger: TriggerType =
		json => {
			noEmitTrigger(json)
			Future.successful(Some(JNothing))
		}

	def noEmitTrigger(json: JObject): Unit
}

trait NoTrigger extends Trigger {
	override def trigger: TriggerType = json => Future.successful(Some(JNothing))
}

trait SimpleEmitTrigger extends Trigger {
	override def trigger: TriggerType = {
		json =>
			Future.successful(simpleEmitTrigger(json))
	}

	def simpleEmitTrigger(json: JObject): Option[JValue]
}



