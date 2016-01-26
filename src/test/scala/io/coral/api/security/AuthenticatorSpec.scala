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

import spray.http.{StatusCodes, BasicHttpCredentials}

abstract class AuthenticatorSpec(mode: String)
	extends BaseAuthSpec(mode) {
	"An Authenticator actor" should {
		"Allow an non-runtime owner if specifically granted" in {
			checkResponseWithNonOwner(List(
				nonOwnerPermission("GET", "/api/runtimes/ab12cd-runtime1/actors", allowed = true)),
				"/api/runtimes/ab12cd-runtime1/actors",
				StatusCodes.OK)
		}

		"Ignore an invalid rule" in {
			checkResponseWithOwner(List(
				ownerPermission("ABC", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.OK)
		}

		"Ignore invalid rules" in {
			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("GET", "", allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.OK)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("", "", allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.OK)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("", null, allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.OK)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("GET", null, allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.OK)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission(null, "", allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.OK)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission(null, null, allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.OK)
		}

		"Authenticate a user based on Basic HTTP credentials" in {
			Get("/api/runtimes").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
				assert(status == StatusCodes.OK)
			}
		}

		"Deny an existing user with the wrong password" in {
			Get("/api/runtimes").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user1, "wrongpassword")) ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.Unauthorized)
			}
		}

		"Deny a request without authentication header" in {
			Get("/api/runtimes") ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.Unauthorized)
			}
		}

		"Deny a non-existing user" in {
			Get("/api").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials("doesnotexist", "wrongpassword")) ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.Unauthorized)
			}
		}

		"Deny a user with an empty password" in {
			Get("/api").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user1, "")) ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.Unauthorized)
			}
		}

		"Deny an empty authentication header" in {
			Get("/api").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials("", "")) ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.Unauthorized)
			}
		}

		"Allow a user that is the owner of a runtime by default" in {
			Get("/api/runtimes/runtime1/actors").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user1, pass1)) ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.OK)
			}
		}

		"Deny an owner of a runtime if specifically denied" in {
			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithOwner(List(
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithOwner(List(
				ownerPermission("GET", "/api/runtimes/runtime1/*", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Deny a wildcard for a path not containing a specific runtime" in {
			checkResponseWithNonOwner(List(
				nonOwnerPermission("GET", "/api/runtimes/*", allowed = true)),
				"/api/runtimes/ab12cd-runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithNonOwner(List(
				nonOwnerPermission("GET", "/*", allowed = true)),
				"/api/runtimes/ab12cd-runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithNonOwner(List(
				nonOwnerPermission("GET", "/api/*", allowed = true)),
				"/api/runtimes/ab12cd-runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Deny multiple sub-paths with a wildcard" in {
			checkResponseWithNonOwner(List(
				nonOwnerPermission("*", "/api/runtimes/ab12cd-runtime1/*", allowed = false),
				nonOwnerPermission("GET", "/api/runtimes/ab12cd-runtime1/*", allowed = false)),
				"/api/runtimes/ab12cd-runtime1/actors",
				StatusCodes.Forbidden)

			Get("/api/runtimes/ab12cd-runtime1/actors/1").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user2, pass2)) ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.Forbidden)
			}
		}

		"Deny multiple sub-paths and multiple methods with wildcards" in {
			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)

			Get("/api/runtimes/ab12cd-runtime1/actors").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user2, pass2)) ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.Forbidden)
			}

			Post("/api/runtimes/ab12cd-runtime1/actors/1").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user2, pass2)) ~> sealRoute(route) ~> check {
				assert(status == StatusCodes.Forbidden)
			}
		}

		"Deny the user access in the case of conflicting rules on the same API level" in {
			checkResponseWithOwner(List(
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/actors", allowed = true),
				ownerPermission("*", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Deny access based on a more specific rule while general rule allows access" in {
			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("*", "/api/runtimes/runtime1/actors", allowed = true),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Deny access based on a more specific rule that allows access while general rule denies access" in {
			checkResponseWithOwner(List(
				ownerPermission("GET", "/api/runtimes/runtime1/*", allowed = false),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/actors", allowed = false),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Deny access based on a rule with an empty method" in {
			checkResponseWithNonOwner(List(
				nonOwnerPermission("", "/api/runtimes/runtime1/*", allowed = true)),
				"/api/runtimes/ab12cd-runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Access denied when specific rule denies access while general rule allows access" in {
			checkResponseWithOwner(List(
				ownerPermission("GET", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/actors", allowed = true),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)

			checkResponseWithOwner(List(
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Access denied when specific rule allows access while general rule denies access" in {
			checkResponseWithOwner(List(
				ownerPermission("*", "/api/runtimes/runtime1/actors", allowed = false),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = true)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Same rule without wildcards overwrites rule with wildcards on the same level" in {
			checkResponseWithOwner(List(
				ownerPermission("GET", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)
		}

		"General rule without wildcards overrides specific rule with wildcards" in {
			checkResponseWithNonOwner(List(
				nonOwnerPermission("GET", "/api/runtimes/ab12cd-runtime1", allowed = false),
				nonOwnerPermission("GET", "/api/runtimes/ab12cd-runtime1/*", allowed = true)),
				"/api/runtimes/ab12cd-runtime1/actors",
				StatusCodes.Forbidden)
		}

		"Specific rule without wildcards overrides general rule with wildcards" in {
			checkResponseWithOwner(List(
				ownerPermission("GET", "/api/runtimes/runtime1/*", allowed = true),
				ownerPermission("GET", "/api/runtimes/runtime1/actors", allowed = false)),
				"/api/runtimes/runtime1/actors",
				StatusCodes.Forbidden)
		}
	}
}