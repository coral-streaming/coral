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

import akka.testkit.TestActorRef
import com.typesafe.config.ConfigFactory
import io.coral.TestHelper
import io.coral.actors.RootActor
import io.coral.api.CoralConfig
import io.coral.api.security.Authenticator.Invalidate
import spray.http.{BasicHttpCredentials, StatusCodes}

class DenyAllAuthenticatorSpec
	extends BaseAuthSpec("deny-all") {
	override def beforeAll() {
		root = TestActorRef[RootActor](new RootActor(), "root")
		admin = system.actorSelection("/user/root/admin")

		cassandra = TestHelper.createCassandraActor(
			config.coral.cassandra.contactPoints.head.getHostName,
			config.coral.cassandra.port)
		TestHelper.prepareTables()
		TestHelper.clearAllTables()

		permissionHandler = system.actorSelection("/user/root/authenticator/permissionHandler")
	}

	override def createActorSystem = {
		val c = new CoralConfig(ConfigFactory.parseString(
			s"""coral.authentication.mode = "deny-all"""".stripMargin)
			.withFallback(ConfigFactory.load()))
		initiateWithConfig(c)
	}

	"A Coral HTTP service with deny all authentication" should {
		"Deny any user" in {
			Get("/api/runtimes").withHeaders(AcceptHeader) ~>
				addCredentials(BasicHttpCredentials(user1, pass1)) ~> route ~> check {
				assert(status == StatusCodes.Unauthorized)
			}
		}
	}
}