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
import io.coral.api.CoralConfig
import spray.http.HttpHeaders.`WWW-Authenticate`
import spray.http.{HttpChallenge, HttpRequest, HttpCredentials}
import spray.routing.RequestContext
import spray.routing.authentication._
import spray.routing.directives.AuthMagnet
import scala.concurrent.{Future, ExecutionContext}

object AcceptAllUser {
	// This needs to be a constant
	val uuid = UUID.fromString("fb5ffab3-3759-4dd7-9d56-0ce2ee7b369f")
	def getAuthInfo() = new AuthInfo(Right(AcceptAllUser("coral", uuid)), List())
}

// A user which requests are always accepted
case class AcceptAllUser(name: String, id: UUID)

object AcceptAllAuthenticator {
	def acceptAllAuthenticator(config: CoralConfig)(implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
		def authenticator(userPass: Option[UserPass]): Future[Option[AuthInfo]] = {
			Future.successful(Some(AcceptAllUser.getAuthInfo()))
		}

		BasicAuth(authenticator _, realm = "Coral")
	}
}

class AcceptAllAuthenticator(config: CoralConfig)(implicit ec: ExecutionContext) extends Authenticator(config) {
	override def getAuthInfo(uniqueUserName: String): Option[AuthInfo] =
		Some(AcceptAllUser.getAuthInfo)
	override def getAuthInfo(userId: UUID): Option[AuthInfo] =
		Some(AcceptAllUser.getAuthInfo)
	override def getAuthInfo(up: UserPass): Option[AuthInfo] =
		Some(AcceptAllUser.getAuthInfo)
}