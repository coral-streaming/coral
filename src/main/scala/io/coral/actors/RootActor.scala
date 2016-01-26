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

package io.coral.actors

import akka.actor._
import akka.util.Timeout
import io.coral.actors.RootActor._
import io.coral.actors.RuntimeAdminActor.DeleteAllRuntimes
import io.coral.actors.database.CassandraActor
import io.coral.api.security.Authenticator.Invalidate
import io.coral.api.security._
import io.coral.api.{ApiServiceActor, CoralConfig}
import io.coral.cluster.{MachineAdmin, ClusterMonitor}
import io.coral.utils.Utils
import akka.pattern.ask
import org.json4s.JObject
import scaldi.Injector
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object RootActor {
	case class CreateHelperActors()
	case class CreateTestActors()
	case class CleanSystem()
	case class CreateAPIHandler()
	case class CreateRuntimeAdmin()
	case class CreateClusterMonitor()
	case class CreateMachineAdmin()
	case class CreateAuthenticator()
	case class CreateCassandraActor(json: Option[JObject])
}

/**
 * The root actor for all actors created in the actor system.
 * This actor should always live under "/user/root".
 * These actors are not created under /user directly on purpose for multiple reasons:
 * - This way, a supervisor strategy can be explicity set, which is not possible
 *   directly under "/user"
 * - Logging can be turned on when an actor crashes or restores (not enabled by default).
 * - The creation and deletion of child actors can be persisted in a central place, if needed.
 * - Other "meta/adminstrator" actions can be programmed in this actor, such as safely
 *   restarting a child actor.
 *
 * Child actors of this actor:
 *    /user/root/api								HTTP API handler
 *    /user/root/admin								Administrator of all runtimes
 *    /user/root/admin/clusterDistributor			Distributes runtimes across nodes
 *    /user/root/clusterMonitor						Monitors cluster events
 *    /user/root/machineAdmin						Handles scaling of cluster
 *    /user/root/authenticator						Authenticates HTTP requests
 *    /user/root/authenticator/permissionHandler	Manages user permissions
 *    /user/root/cassandra							Handles Cassandra database interaction
 */
class RootActor(implicit injector: Injector, ec: ExecutionContext,
				config: CoralConfig) extends Actor with ActorLogging {
	// The default strategy is to log the error and resume the actor
	override def supervisorStrategy = SupervisorStrategies.logAndContinue(log)

	var apiHandler: ActorRef = _
	var runtimeAdmin: ActorRef = _
	var clusterDistributor: ActorRef = _
	var clusterMonitor: ActorRef = _
	var machineAdmin: ActorRef = _
	var authenticator: ActorRef = _
	var permissionHandler: ActorRef = _
	var cassandra: ActorRef = _

	override def preStart() = {

	}

	override def receive = {
		case CreateHelperActors() =>
			createHelperActors()
		case CreateTestActors() =>
			createTestActors()
		case CleanSystem() =>
			cleanSystem()
		case CreateAPIHandler() =>
			createAPIHandler()
		case CreateRuntimeAdmin() =>
			createRuntimeAdmin()
		case CreateClusterMonitor() =>
			createClusterMonitor()
		case CreateMachineAdmin() =>
			createMachineAdmin(Some(sender()))
		case CreateAuthenticator() =>
			createAuthenticator()
		case CreateCassandraActor(json) =>
			createCassandraActor(json)
		case _ =>
	}

	def cleanSystem() {
		implicit val timeout = Timeout(5.seconds)
		runtimeAdmin.ask(DeleteAllRuntimes(), timeout.duration).flatMap(_ =>
			authenticator.ask(Invalidate(), timeout.duration))
	}

	/**
	 * Create all helper actors and return the service handler
	 * that handles HTTP requests. In case of an error, print
	 * the error to the console and quit the application.
	 */
	def createHelperActors() = {
		val originalSender = sender()

		try {
			createClusterMonitor()
			createMachineAdmin(None)
			createCassandraActor(None)
			createAuthenticator()
			createRuntimeAdmin()
			val serviceActor = createAPIHandler()
			sender ! serviceActor
		} catch {
			case e: Exception =>
				Utils.printFatalError(e)
		}
	}

	def createTestActors() = {
		createClusterMonitor()
		createMachineAdmin(None)
		createCassandraActor(None)
		createRuntimeAdmin()
	}

	/**
	 * Create the cluster monitor actor, based on the
	 * CoralConfig implicitly provided.
	 */
	def createClusterMonitor() {
		log.info("Creating cluster monitor")
		clusterMonitor = context.actorOf(Props(new ClusterMonitor(config)), "clusterMonitor")
	}

	/**
	 * Create a new machineAdmin actor.
	 */
	def createMachineAdmin(sender: Option[ActorRef]) {
		log.info("Creating machine admin")

		if (sender.isDefined) {
			// If sender is defined, this means this node has joined the cluster
			// while the system is running.
			// config.coral.cluster.enabled is still false but we cannot
			// change it because it is a read-only property set from the start.
			log.info("Creating admin actor on standalone node")

			config.coral.cluster.joined = true
			machineAdmin = context.actorOf(Props(new MachineAdmin()), "machineAdmin")

			sender.get ! true
		} else {
			if (config.coral.cluster.enabled) {
				machineAdmin = context.actorOf(Props(new MachineAdmin()), "machineAdmin")
			} else {
				log.warning("Not creating machine admin because cluster mode is not enabled.")
			}
		}
	}

	/**
	 * Create a new Cassandra actor with the configuration properties
	 * as specified in the CoralConfig object.
	 */
	def createCassandraActor(json: Option[JObject]) {
		val originalSender = sender()
		log.info("Creating cassandra actor")

		json match {
			case None =>
				val props = CassandraActor.fromConfig(config)
				cassandra = context.actorOf(props, "cassandra")
			case Some(j) =>
				val props = CassandraActor(j)

				if (props.isDefined) {
					cassandra = context.actorOf(props.get, "cassandra")
					originalSender ! cassandra
				}
		}
	}

	/**
	 * Create a new authenticator object that handles user
	 * authentication requests. The exact type created depends on
	 * the configuration specified in the CoralConfig object.
	 */
	def createAuthenticator() {
		log.info("Creating authenticator")

		// Set the configuration file for the ApiService
		AuthenticatorChooser.config = Some(config)

		config.coral.authentication.mode match {
			case "coral" =>
				context.actorOf(Props(new CoralAuthenticator(config)), "authenticator")
			case "ldap" =>
				context.actorOf(Props(new LDAPAuthenticator(config)), "authenticator")
			case "accept-all" =>
				context.actorOf(Props(new AcceptAllAuthenticator(config)), "authenticator")
		}
	}

	/**
	 * Create a new runtime admin actor. This is the parent of all
	 * runtime actors on the platform (at least, on this node).
	 */
	def createRuntimeAdmin() {
		log.info("Creating runtime admin")
		runtimeAdmin = context.actorOf(Props(new RuntimeAdminActor()), "admin")
	}

	/**
	 * Create a new API handler actor. This actor handles all HTTP
	 * requests and forwards the request to the proper actor.
	 */
	def createAPIHandler(): ActorRef = {
		log.info("Creating API handler")
		apiHandler = context.actorOf(Props(new ApiServiceActor()), "api")
		apiHandler
	}
}