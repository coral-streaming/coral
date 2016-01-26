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

import spray.http.HttpRequest
import io.coral.api.Runtime

/**
 * Contains authorization information for a certain user.
 * Contains a reference to the user and a map of all permissions.
 * @param user The user to which this AuthInfo class applies.
 * @param permissions A table with permissions.
 */
case class AuthInfo(user: Either[User, AcceptAllUser], permissions: List[Permission]) {
	/**
	 * Check whether this user has permission to perform a certain action.
	 * The default permission is that a user always has full permissions
	 * for a runtime he created, unless specifically denied. A user by default
	 * has no permission to access other runtimes he did not create
	 * unless specifically granted. This means that additional users that
	 * needs access to a runtime need to get an entry in this table.
	 * @return True when access is allowed, false otherwise.
	 */
	def hasPermission(request: HttpRequest, runtime: Option[Runtime]): Boolean = {
		user match {
			case Left(u) =>
				val method = request.method.name.toUpperCase
				val uri = request.uri.toRelative.toString()

				runtime match {
					case None =>
						true // ... Handle this case, when is this happening?
					case Some(r) => {
						// When the user is the owner, a permission with method "*",
						// uri /api/runtime/<id> and allowed = true is present into the table.
						val isOwnerOfRuntime = permissions.exists(p => {
							p.user == u.id &&
							// The method must match a wildcard exactly
							p.method == "*" &&
							// The uri must match this exactly
							p.uri == "/api/runtimes/" + r.name + "/*" &&
							p.runtime == r.id &&
							p.allowed
						})

						if (isOwnerOfRuntime) {
							// Is owner but can still be specifically denied to specific URL
							val specificallyDenied = permissions.exists(p => {
								p.user == u.id &&
								// The method can be a wildcard or can be a verb
								(p.method == method || p.method == "*") &&
								(p.uri == uri || uri.startsWith(startOf(p.uri))) &&
								p.runtime == r.id &&
								!p.allowed
							})

							!specificallyDenied
						} else {
							// Not the owner, can still be specifically granted
							val specificallyGranted = permissions.find(p => {
								p.user == u.id &&
								(p.method == method || p.method == "*") &&
								(p.uri == uri || uri.startsWith(startOf(p.uri))) &&
								p.runtime == r.id &&
								p.allowed
							})

							specificallyGranted match {
								case None => false
								case Some(grant) => {
									// Specifically granted, but can still be overruled
									val overruled = permissions.find(p => {
										p.user == u.id &&
										(p.method == method || p.method == "*") &&
										(p.uri == uri || uri.startsWith(startOf(p.uri))) &&
										p.runtime == r.id &&
										!p.allowed
									})

									overruled match {
										// No permission that overrules this grant
										case None => grant.allowed
										case Some(or) =>
											// Not the owner, allowed if specifically granted
											// and not specifically overruled by a deny.
											grant.allowed && or.allowed
									}
								}
							}
						}
					}
				}
			// The "accept all" user has, by definition, always access
			case Right(acceptAll) =>
				true
		}
	}

	/**
	 *  A rule is more specific if the less specific rule has
	 * defined * as method and the more specific one has not,
	 * or the less specific method has a path that is a subpath
	 * of the more specific one, or contains a wildcard.
	 * Examples (Permission(method, uri)):
	 * - Permission("*", "/api/runtimes/1") less specific than
	 *   Permission("GET", "/api/runtimes/1")
	 *
	 * - Permission("GET", "/api/runtimes/1/<star>") less specific than
	 *   Permission("GET", "/api/runtimes/1/actors")
	 *
	 * - Permission("GET", "/api/runtimes/1/") less specific than
	 *   Permission("*", "/api/runtimes/1/actors")
	 */
	def moreSpecificThan(p1: Permission, p2: Option[Permission]): Boolean = {
		p2 match {
			case None => false
			case Some(p) =>
				(p1.method == p.method || p.method == "*") &&
				p1.user == p.user &&
				p1.runtime == p.runtime &&
				(p1.uri == p.uri || isChildPathOf(p1.uri, p.uri))
		}
	}

	/**
	 * /api/runtime/1/actors/1 is a child path of /api/runtime1/actors
	 * /api/runtime/1/<star> is a child path of /api/runtime/1
	 */
	def isChildPathOf(uri1: String, uri2: String): Boolean = {
		uri1.startsWith(uri2)
	}

	/**
	 * Obtains the start of a permission rule ending with an asterisk (*).
	 * This is the URI without the *, except when the URI is shorter than 14 characters.
	 * Since each URI must start with "/api/runtimes/", this is at least 14 chars.
	 * In that case, return the entire URI, which is always an invalid path.
	 */
	def startOf(uri: String): String = {
		if (uri.endsWith("*")) {
			val result = uri.substring(0, uri.length - 1)
			// Do not allow wildcards like /api/runtime/*
			if (result.length < 14) uri else result
		} else {
			uri
		}
	}

	/**
	 * Obtain the unique name for this AuthInfo object.
	 * In the case of a specific user, it is the unique name of the user.
	 * In the case of the AcceptAll user, it is "coral".
	 */
	def uniqueName(): String = {
		user match {
			case Left(u) => u.uniqueName
			case Right(acceptAll) => acceptAll.name
		}
	}

	/**
	 * Obtain the UUID for this AuthInfo object.
	 * In the case of a specific user, it is the UUID belonging to that user.
	 * In the case of the AcceptAll user, it is a constant UUID.
	 */
	def id(): UUID = {
		user match {
			case Left(u) => u.id
			case Right(acceptAll) => acceptAll.id
		}
	}
}