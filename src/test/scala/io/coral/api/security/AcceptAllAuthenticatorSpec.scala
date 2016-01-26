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
import com.typesafe.config.ConfigFactory
import io.coral.api.CoralConfig
import spray.http.{BasicHttpCredentials, StatusCodes}

class AcceptAllAuthenticatorSpec
	extends BaseAuthSpec("accept-all") {
	val acceptAllUUIDString = "fb5ffab3-3759-4dd7-9d56-0ce2ee7b369f"
	val acceptAllUUID = UUID.fromString(acceptAllUUIDString)

	override def createActorSystem = {
		val c = new CoralConfig(ConfigFactory.parseString(
			s"""coral.authentication.mode = "accept-all"
			   |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
			   |coral.cluster.enable = true
			 """.stripMargin)
			.withFallback(ConfigFactory.load()))
		initiateWithConfig(c)
	}

	"A Coral HTTP service with no authentication" should {
		"Allow all users without credentials" in {
			// No credentials on purpose
			Get("/api/runtimes").withHeaders(AcceptHeader) ~> route ~> check {
				assert(status == StatusCodes.OK)
			}
		}

		"Allow all users with credentials" in {
			// Wrong credentials on purpose
			Get("/api/runtimes").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user1, "wrongpassword")) ~> route ~> check {
				assert(status == StatusCodes.OK)
			}
		}
	}
}