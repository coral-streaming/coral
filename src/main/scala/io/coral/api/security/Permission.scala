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

package io.coral.api.security

import java.util.UUID
import org.json4s.JObject
import io.coral.api.Runtime

object Permission {
	implicit val formats = org.json4s.DefaultFormats

	def isOwnerPermission(p: Permission, runtime: Runtime): Boolean = {
		p.user == runtime.owner &&
		p.method == "*" &&
		p.uri == s"/api/runtimes/${runtime.name}/*" &&
		p.allowed
	}

	def fromJson(permission: List[JObject]): List[Permission] = {
		permission.map(fromJson)
	}

	def fromJson(permission: JObject): Permission = {
		val id = UUID.fromString((permission \ "id").extract[String])
		val user = UUID.fromString((permission \ "user").extract[String])
		val runtime = UUID.fromString((permission \ "runtime").extract[String])
		val method = (permission \ "method").extract[String]
		val uri = (permission \ "uri").extract[String]
		val allowed = (permission \ "allowed").extract[Boolean]

		Permission(id, user, runtime, method, uri, allowed)
	}
}

/**
 * Represents a permission. A permission is a combination of the following:
 * - an UUID uniquely identifying the permission
 * - the UUID of a user to which the permission belongs
 * - The UUID of the runtime on which the permission applies
 * - a method (GET, PUT, POST, DELETE, PATCH or * for all)
 * - an URL (/api/runtime1/actors or /api/runtime1/<star> for all children)
 * - a boolean parameter stating whether access is allowed or not
 */
case class Permission(// The unique identifier of the permission
					  id: UUID,
					  // The owner name of the permission
					  user: UUID,
					  // The name of the runtime that the permission applies to
					  runtime: UUID,
					  // The method of the permission
					  method: String,
					  // The URI of the permission
					  uri: String,
					  // Whether allowed or denied
					  allowed: Boolean)