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

import org.json4s._
import org.json4s.jackson.JsonMethods._

object ApiErrors {
	// json4s "~" does not mix with Spray.io "~", therefore do it like this
	def runtimeNotFound(id: String) = parse(
		s"""{ "action": "Find runtime", "success": false,
		   |"reason": "Runtime with ID '$id' not found." }""".stripMargin).asInstanceOf[JObject]
	def invalidActorId(actorName: String) = parse(
		s"""{ "action": "Get actor", "success": false,
		   |"reason": "Invalid actor ID ('$actorName') provided." }""".stripMargin).asInstanceOf[JObject]
	def actorNotFound(actorName: String) = parse(
		s"""{ "action": "Get actor", "success": false,
		   |"reason": "Actor with ID '$actorName' not found." }""".stripMargin).asInstanceOf[JObject]
	def permissionNotFound(id: String) = parse(
		s"""{ "action": "Get permission", "success": false,
		   |"reason": "Permission with ID '$id' not found."}""".stripMargin).asInstanceOf[JObject]
	def machineNotFound(id: String) = parse(
		s"""{ "action": "Get machine", "success": false,
		   |"reason": "Machine with given name not found."}""".stripMargin).asInstanceOf[JObject]
	def invalidJoinLeaveRequest(option: String) = parse(
		s"""{ "action": "Join or leave cluster", "success": false,
		   |"reason": Invalid option provided" }""".stripMargin).asInstanceOf[JObject]
	def mimeTypeIncorrect() = parse(
		s"""{ "action": "Set HTTP header", "success": false,
		   |"reason": "specified mime type not supported to be returned by the server"} """
			.stripMargin).asInstanceOf[JObject]
	def notLoggedInUser() = parse(
		s"""{ "action": "Create runtime", "success": false,
		   |"reason": "Owner of the runtime is not equal to the logged in user."} """
			.stripMargin).asInstanceOf[JObject]
	def invalidPermissionId() = parse(
		s"""{ "action": Remove permission", "success": false,
		   |"reason": "Failed to parse UUID for permission to remove."} """
			.stripMargin).asInstanceOf[JObject]
}