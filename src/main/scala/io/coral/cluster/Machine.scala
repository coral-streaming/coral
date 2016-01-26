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

package io.coral.cluster

import org.json4s.JObject
import org.json4s.JsonAST.{JNull, JNothing}
import org.json4s.JsonDSL._

/**
 * Represents a single machine in the cluster.
 * @param alias A potential friendly name for this machine (an alias)
 * @param ip The IP address of the machine
 * @param port The port to which akka connects to this machine
 * @param status The status of the machine,
 *               e.g. "up", "down", "unreachable", etc.
 */
case class Machine(// The friendly name of the machine, if any
				   alias: Option[String],
				   // The IP address of the machine
				   ip: String,
				   // The port on which the machine can be reached
				   port: Int,
				   // The role of the machine
				   roles: List[String],
				   // The status of the machine
				   status: Option[String]) {
	def toJson(): JObject = {
		("alias" -> alias.orNull) ~
		("ip" -> ip) ~
		("port" -> port) ~
		("roles" -> roles) ~
		("status" -> status)
	}
}