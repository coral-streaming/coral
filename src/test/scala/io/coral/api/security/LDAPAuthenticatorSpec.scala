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

import java.net.InetAddress
import akka.actor.Props
import akka.testkit.TestActorRef
import com.typesafe.config.ConfigFactory
import com.unboundid.ldap.listener.{InMemoryListenerConfig, InMemoryDirectoryServer, InMemoryDirectoryServerConfig}
import io.coral.TestHelper
import io.coral.actors.RootActor
import io.coral.actors.RootActor.{CreateHelperActors, CreateTestActors}
import io.coral.api.CoralConfig
import io.coral.api.security.Authenticator.Invalidate
import io.coral.cluster.ClusterMonitor

class LDAPAuthenticatorSpec
	extends AuthenticatorSpec("ldap") {
	override def createActorSystem = {
		val c = new CoralConfig(ConfigFactory.parseString(
			s"""{
			   |akka.actor.provider = "akka.actor.LocalActorRefProvider"
			   |coral {
			   |  cluster.enable = false
			   |  authentication {
			   |    mode = "ldap"
			   |    ldap {
			   |      host = "${InetAddress.getLocalHost.getHostAddress}"
			   |      port = 1234
			   |      bind-dn = "uid={user},ou=People,dc=example,dc=com"
			   |      mfa {
			   |        enabled = false
			   |      }
			   |    }
			   |  }
			   |}}""".stripMargin).withFallback(ConfigFactory.load()))
		initiateWithConfig(c)
	}

	var ldapServer: InMemoryDirectoryServer = _

	override def beforeAll() {
		startInMemoryLDAPServer()

		root = TestActorRef[RootActor](new RootActor(), "root")
		admin = system.actorSelection("/user/root/admin")

		root ! CreateHelperActors()

		cassandra = TestHelper.createCassandraActor(
			config.coral.cassandra.contactPoints.head.getHostName,
			config.coral.cassandra.port)
		TestHelper.prepareTables()
		TestHelper.clearAllTables()

		system.actorOf(Props(new ClusterMonitor(config)), "clusterMonitor")
		authenticator = system.actorSelection("/user/root/authenticator")
		permissionHandler = system.actorSelection("/user/root/authenticator/permissionHandler")

		runtimeUUID1 = TestHelper.createStandardRuntime("runtime1", user1, admin)
		runtimeUUID2 = TestHelper.createStandardRuntime("runtime2", user2, admin)
		runtimeUUID3 = TestHelper.createStandardRuntime("runtime3", user3, admin)

		userUUID1 = TestHelper.getUserUUIDFromUniqueName(authenticator, user1)
		userUUID2 = TestHelper.getUserUUIDFromUniqueName(authenticator, user2)
		userUUID3 = TestHelper.getUserUUIDFromUniqueName(authenticator, user3)

		authenticator ! Invalidate()

		Thread.sleep(500)
	}

	def startInMemoryLDAPServer() {
		val config = new InMemoryDirectoryServerConfig("dc=example,dc=com")
		config.setSchema(null)
		config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig(
			"LDAP listener", InetAddress.getLocalHost, 1234, null))
		config.addAdditionalBindCredentials("cn=Directory Manager", "password")
		ldapServer = new InMemoryDirectoryServer(config)
		val testDataFile = getClass.getResource("testdata.ldif").getFile
		ldapServer.importFromLDIF(true, testDataFile)
		ldapServer.startListening()
	}

	override def afterAll() {
		ldapServer.shutDown(true)
	}

	"An LDAP actor" should {
		"Properly create a connection" in {
			/*
			val ldapActor = authenticator.underlyingActor.asInstanceOf[LDAPAuthenticator]

			assert(ldapActor.ldapConn.isDefined)
			assert(ldapActor.permissionHandler != null)
			assert(ldapActor.runtimes.size == 3)
			assert(ldapActor.users.size == 3)

			TestHelper.createStandardRuntime("runtime4", "ab12cd", admin)
			Thread.sleep(500)

			authenticator ! Invalidate()
			Thread.sleep(500)

			assert(ldapActor.runtimes.size == 4)
			assert(ldapActor.users.size == 3)

			val user = ldapActor.users.get("ab12cd")
			assert(user.isDefined)

			assert(user.get.permissions.size == 4)
			*/
		}
	}
}