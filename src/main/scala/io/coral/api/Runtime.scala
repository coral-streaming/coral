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

package io.coral.api

import java.util.UUID
import io.coral.utils.Utils
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

/**
 * Represents a runtime. This is a read-only, cached version of the
 * "truth" present in the runtimes table in Cassandra.
 * @param name The unique name of the runtime
 * @param status The status of the runtime.
 *               0 = created
 *               1 = running
 *               2 = stopped
 *               3 = invalid
 *               4 = deleted
 * @param projectId The projectId this runtime belongs to
 * @param startTime The time this runtime was started.
 */
case class Runtime(// The unique id of the runtime
				   id: UUID,
				   // The owner of the runtime
				   owner: UUID,
				   // The name of the runtime (without owner name)
				   name: String,
				   // The unique name of the owner ("owner-runtimeName")
				   uniqueName: String,
				   // The full path of the runtime admin actor that manages this runtime
				   adminPath: String,
				   // The current status of the runtime
				   status: Int,
				   // The corresponding projectId of the runtime
				   projectId: Option[UUID],
				   // The JSON definition of the runtime
				   jsonDef: JObject,
				   // Runtime statistics for the runtime
				   runtimeStats: Option[RuntimeStatistics],
				   // The timestamp the runtime started on
				   startTime: Long) {
	def toJson(): JObject = {
		("id" -> JString(id.toString)) ~
		("owner" -> JString(owner.toString)) ~
		("name" -> JString(name)) ~
		("uniqueName" -> JString(uniqueName)) ~
		("status" -> friendly(status)) ~
		("project" -> JString(projectId.toString)) ~
		("json" -> jsonDef) ~
		("starttime" -> Utils.friendlyTime(startTime))
	}

	def friendly(status: Int): String = {
		status match {
			case 0 => "created"
			case 1 => "running"
			case 2 => "stopped"
			case 3 => "invalid"
			case 4 => "deleted"
			case _ => "unrecognized status"
		}
	}
}