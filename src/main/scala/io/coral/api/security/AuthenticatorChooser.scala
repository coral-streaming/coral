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

import akka.actor.ActorSelection
import akka.event.slf4j.Logger
import spray.http.HttpRequest
import spray.routing.directives.AuthMagnet
import io.coral.api.{Runtime, CoralConfig}
import scala.concurrent.ExecutionContext

object AuthenticatorChooser {
	var config: Option[CoralConfig] = None
}

/**
 * Four ways to authenticate:
 * 1) Accept all, no checks on authentication, no checks on authorization
 * 2) Deny all, all requests are denied
 * 3) Coral internal, users and passwords managed by Coral platform
 * 4a) LDAP without MFA: Through LDAP server but no MFA
 * 4b) LDAP with MFA: Through LDAP server with MFA
 */
class AuthenticatorChooser() {
	// When the application is started, it is not defined
	var authenticationMode: Option[String] = None

	def authenticator(cassandra: ActorSelection,
					  authenticatorActor: ActorSelection)
					 (implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
		// This is only set the first time the API service is run
		if (!authenticationMode.isDefined) {
			setAuthenticationMode(AuthenticatorChooser.config)
		}

		authenticationMode match {
			case Some("accept-all") =>
				AcceptAllAuthenticator.acceptAllAuthenticator(AuthenticatorChooser.config.get)
			case Some("coral") =>
				CoralAuthenticator.coralAuthenticator(cassandra, authenticatorActor)
			case Some("ldap") =>
				LDAPAuthenticator.ldapAuthenticator(authenticatorActor)
			case other =>
				throw new Exception(s"Invalid authentication mode provided: $other")
		}
	}

	def setAuthenticationMode(config: Option[CoralConfig]) {
		authenticationMode = config match {
			case None => None
			case Some(c) => c.coral.authentication.mode match {
				case "accept-all" => Some("accept-all")
				case "coral" => Some("coral")
				case "ldap" => Some("ldap")
				case _ =>
					Logger(getClass.getName).error("[FATAL ERROR]: Invalid authentication mode provided. Exiting.")
					System.exit(1)
					None
			}
		}
	}

	def coralAuthorizer(request: HttpRequest, authInfo: AuthInfo, runtime: Runtime)(
		implicit ec: ExecutionContext): Boolean = {
			authInfo.hasPermission(request, Some(runtime))
	}

	def coralAuthorizer(request: HttpRequest, authInfo: AuthInfo)(implicit ec: ExecutionContext): Boolean = {
		authInfo.hasPermission(request, None)
	}
}