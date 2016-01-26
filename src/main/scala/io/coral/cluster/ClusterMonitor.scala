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

package io.coral.cluster

import akka.actor.{ActorLogging, Address, Actor}
import akka.cluster.{Member, Cluster}
import akka.cluster.ClusterEvent._
import io.coral.actors.RuntimeAdminActor
import akka.pattern.ask
import io.coral.api.{CoralConfig, RuntimeStatistics}
import akka.actor._
import io.coral.cluster.ClusterMonitor._
import io.coral.utils.Utils
import org.json4s._
import org.json4s.JsonAST.JArray
import org.json4s.JsonDSL._
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.pipe
import scala.concurrent.{Await, ExecutionContext, Future}

object ClusterMonitor {
	case class GetClusterInfo()
	case class GetAddresses(actorName: String)
	case class GetPlatformStatistics()
	case class GetMachineStatistics(machine: Machine)
	case class RefreshCluster(enabled: Boolean)
}

/**
 * Class to monitor cluster-level events.
 * This class is started on every node that runs the
 * startup class io.coral.api.Boot.
 */
class ClusterMonitor(config: CoralConfig)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
	implicit val timeout = Timeout(5.seconds)
	implicit val formats = org.json4s.DefaultFormats

	var cluster: Option[Cluster] = None
	setCluster(config.coral.cluster.enabled)

	override def preStart() {
		cluster match {
			case Some(c) =>
				c.subscribe(self, initialStateMode = InitialStateAsSnapshot,
					classOf[MemberEvent], classOf[UnreachableMember])
			case None =>
				log.info("Not subscribing cluster monitor to cluster events since cluster is disabled.")
		}
	}

	override def postStop() = {
		cluster match {
			case Some(c) =>
				c.unsubscribe(self)
			case None =>
		}
	}

	override def receive = {
		case MemberUp(member) =>
			log.info("Member up: " + member)
		case UnreachableMember(member) =>
			// TODO: Restart runtimes on potentially crashed node
			// restartRuntimes(member)
		case MemberExited(member) =>
			invalidateSelfIfRemoved(member)
		case MemberRemoved(member, previousState) =>
			log.info("Member removed: " + member)
		case LeaderChanged(member) =>
			log.info("Leader changed: " + member)
		case GetClusterInfo() =>
			sender ! getClusterInfo()
		case GetAddresses(actorName) =>
			sender ! getAddresses(actorName)
		case GetPlatformStatistics() =>
			getPlatformStatistics()
		case RefreshCluster(enabled) =>
			setCluster(enabled)
		case _ =>
	}

	def setCluster(enabled: Boolean) = {
		if (enabled) {
			try {
				cluster = Some(Cluster(context.system))
			} catch {
				case e: akka.ConfigurationException =>
					log.error(e.getMessage())
			}
		} else {
			log.info("Not starting cluster monitoring because cluster is disabled.")
		}
	}

	/**
	 * When it is removed, send the removed message
	 * no matter what is received.
	 */
	def removed: Receive = {
		case _ => sender ! removedInfo()
	}

	def removedInfo(): JObject = {
		("success" -> false) ~
		("reason" -> "This node has been removed from the cluster.")
	}

	/**
	 * Restart the runtimes that were running on the unreachable node.
	 * The cluster monitor asks the cluster distributor to restart the runtimes
	 * that have crashed.
	 * @param member The member that became unreachable.
	 */
	def restartRuntimes(member: Member) {
		val originalSender = sender()

		val clusterDistributor = context.actorSelection("/user/root/admin/clusterDistributor")
		val future = clusterDistributor.ask(ClusterDistributor.RestartRuntimes(member))

		future pipeTo originalSender
	}

	/**
	 * Obtain cluster membership info for all nodes in a cluster.
	 * This contains the leader, the roles present in the cluster,
	 * and for each member its unique address, its status and its role.
	 * @return A JObject containing this information.
	 */
	def getClusterInfo(): JObject = {
		cluster match {
			case None =>
				// Running in nocluster mode, just show local machine
				val selfPath = Utils.getFullSelfPath(self)

				if (selfPath.startsWith("akka://")) {
					val message = "Cannot obtain cluster information when using LocalActorRefProvider"
					log.error(message)

					("action" -> "Get cluster info") ~
					("success" -> false) ~
					("reason" -> message)
				} else {
					// Using ClusterActorRefProvider
					val address = Utils.addressFromPath(selfPath)
					addressToJson(address)
				}
			case Some(c) =>
				if (currentExiting(c)) {
					removedInfo()
				} else {
					val members: List[JsonAST.JObject] = c.state.members.map(m => {
						val address = m.uniqueAddress.address

						addressToJson(address) ~
							("status" -> m.status.toString) ~
							("roles" -> m.roles.toList)
					}).toList

					val membersWithLeader: List[JObject] = if (c.state.leader.isDefined) {
						val leader: JObject =
							addressToJson(c.state.leader.get) ~
								("status" -> "Up") ~
								("roles" -> JArray(List()))

						// Only add the leader to the members if it is not already in members
						if (members.contains(leader)) members else leader :: members
					} else {
						members
					}

					val withoutDebug = membersWithLeader.filter(m =>
						!((m \ "roles").extract[List[String]].contains("debug-helper")))

					("leader" -> c.state.leader.getOrElse("None").toString) ~
					("roles" -> c.state.allRoles.toList.filter(_ != "debug-helper")) ~
					("members" -> JArray(withoutDebug))
				}
		}
	}

	/**
	 * Check whether the current node is exiting from the cluster.
	 */
	def currentExiting(c: Cluster): Boolean = {
		// Is the current node exiting? Then consider it left
		val exiting = c.state.members.filter(m => {
			m.status.toString == "Exiting" || m.status.toString == "Leaving"
		})

		val currentExiting = exiting.exists(member => {
			Utils.getFullSelfPath(self).startsWith(member.uniqueAddress.address.toString)
		})

		if (currentExiting) {
			log.warning("Current node is exiting, considering it left")
		}

		currentExiting
	}

	/**
	 * Convert an address object to a JSON object.
	 * @param address The address to use.
	 * @return A JSON representation of the address.
	 */
	def addressToJson(address: Address) = {
		("address" ->
			("fullAddress" -> address.toString) ~
			("protocol" -> address.protocol) ~
			("system" -> address.system) ~
			("ip" -> address.host.getOrElse("None")) ~
			("port" -> address.port.getOrElse(-1)))
	}

	/**
	 * Change the answer of this node if it has been removed from
	 * the cluster.
	 * @param member The member that was removed. If this node is that member,
	 *               then change the status of this node to removed.
	 */
	def invalidateSelfIfRemoved(member: Member) = {
		val removedMember: Address = member.uniqueAddress.address
		val selfPath = Utils.getFullSelfPath(self)
		val selfAddress = Utils.addressFromPath(selfPath)

		// When the address matches, this node was the one removed from the cluster
		if (selfAddress == removedMember) {
			// Change behavior to always respond with removed message
			context.become(removed)

			// The node has become unreachable, but runtimes running on the
			// node may still continue to operate and may still read and
			// write to Cassandra, read and write to Kafka, generate data
			// and call API's. How should this be prevented?

			// TODO: Block all kafka consumers and producers
			// TODO: Block all Cassandra connections
			// TODO: Block all data generators
			// TODO: Block all HTTP client actors
		}
	}

	/**
	 * Update the runtime statistics of the entire platform.
	 * This means that all meta-information, information about each
	 * runtime and information about each actor in each runtime
	 * is obtained.
	 */
	def getPlatformStatistics() {
		def getClusterSize(clusterInfo: JObject): Future[Int] = {
			Future {
				val array = (clusterInfo \ "members").extractOpt[JArray]
				if (array.isDefined) {
					array.get.arr.size
				} else {
					0
				}
			}.mapTo[Int]
		}

		val originalSender = sender()
		val adminActor = context.actorSelection("/user/root/admin")

		val future: Future[JObject] = try {
			for {
				clusterInfo <- Future(getClusterInfo()).mapTo[JObject]
				list <- adminActor.ask(RuntimeAdminActor.GetAllRuntimeStatistics()).mapTo[List[RuntimeStatistics]]
				merged <- Future(RuntimeStatistics.merge(list))
				jobject <- Future(RuntimeStatistics.toJson(merged))
				totalMachines <- getClusterSize(clusterInfo)
				totalRuntimes <- Future.successful(list.size)
				counters <- Future((jobject \ "counters"))
			} yield {
				// When the node is exiting the cluster or is not part of a cluster any more,
				// simply return the cluster info message. Else, show other info as well.
				val clusterSuccess = (clusterInfo \ "success").extractOpt[Boolean]
				val clusterFailed = clusterSuccess.isDefined && clusterSuccess.get == false

				if (!clusterFailed) {
					val result: JObject =
						("totalMachines" -> totalMachines) ~
						("totalRuntimes" -> totalRuntimes) ~
						("totalActors" -> merged.totalActors) ~
						("totalMessages" -> merged.totalMessages) ~
						("totalExceptions" -> merged.totalExceptions) ~
						("counters" -> counters) ~
						("cluster" -> clusterInfo)
					result
				} else {
					clusterInfo
				}
			}
		} catch {
			case e: Exception =>
				log.error(e.getMessage)
				e.printStackTrace()
				Future.failed(e)
		}

		future pipeTo originalSender
	}

	/**
	 * Get all addresses for a user actor living on each node
	 * in the cluster. An example of such an actor is the ClusterMonitor
	 * actor itself, or the CassandraActor. These actors have as many instances
	 * as there are machines, one on each machine.
	 * @param actorName The name of the actor
	 * @return A list of actor paths on each of the nodes in the cluster.
	 */
	def getAddresses(actorName: String): List[String] = {
		cluster match {
			case None =>
				List("akka://coral/user/" + actorName)
			case Some(c) =>
				c.state.members
				// Ignore debug helpers here because they are only used in tests
				// and they do not have authenticators and other supporting actors.
				.filter(!_.roles.contains("debug-helper"))
				.map(m => { m.uniqueAddress.address.toString + "/user/root/" + actorName})
				.toList
		}
	}
}