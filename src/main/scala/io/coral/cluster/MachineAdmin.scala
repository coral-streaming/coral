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

import java.util.regex.Pattern
import akka.actor.{Actor, ActorLogging, Address}
import akka.cluster.{Member, Cluster}
import akka.util.Timeout
import io.coral.api.CoralConfig
import io.coral.cluster.ClusterMonitor.RefreshCluster
import io.coral.cluster.MachineAdmin._
import io.coral.utils.Utils
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.collection.mutable.{Map => mMap}
import akka.pattern.ask
import org.json4s._
import org.json4s.JsonDSL._
import akka.pattern.pipe

object MachineAdmin {
	case class GetMachine(machineName: String)
	case class GetMachines()
	case class JoinCluster(json: JObject)
	case class AddMachines(json: JObject)
	case class LeaveCluster()
	case class GetMachineAlias(ip: String, port: Int)

	case class ClusterAddress(fullAddress: String, protocol: String, system: String, ip: String, port: Int)
	case class ClusterMemberInfo(address: ClusterAddress, status: String, roles: List[String])
}

/**
 * Actor that handles global cluster settings changes. This currently
 * contains adding machines to the cluster and removing machines from the
 * cluster.
 */
class MachineAdmin(implicit ec: ExecutionContext, config: CoralConfig) extends Actor with ActorLogging {
	implicit val formats = org.json4s.DefaultFormats
	implicit val timeout = Timeout(3.seconds)
	val cluster = Cluster(context.system)

	// <alias, Machine>
	val machineAliasMap = mMap.empty[String, Machine]

	val validIpv4 = Pattern.compile(
		"^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
		  "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
		  "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
		  "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")

	override def receive = {
		case GetMachine(machineName) =>
			getMachine(machineName)
		case GetMachines() =>
			getMachines()
		case JoinCluster(json) =>
			joinCluster(json)
		case AddMachines(json) =>
			addMachines(json)
		case LeaveCluster() =>
			leaveCluster()
		case GetMachineAlias(ip, port) =>
			getMachineAlias(ip, port)
		case _ =>
	}

	/**
	 * Get a machine object by its alias or IP address
	 * @param machineNameOrIp The machine alias or the IP address.
	 *                        Which one it is will be determined by this method,
	 *                        if an IP address can be parsed from the string it will
	 *                        use it as an IP address, otherwise an alias.
	 * @return The Machine object with the information of the requested machine
	 */
	def getMachine(machineNameOrIp: String) {
		val originalSender = sender()

		def isIp(str: String): Boolean = {
	  		validIpv4.matcher(machineNameOrIp).matches
    	}

		val result = if (isIp(machineNameOrIp)) {
			// Try fetching machine by IP address
			val member: Option[Member] = cluster.state.members.find(
				_.uniqueAddress.address.toString == machineNameOrIp)
			memberToMachine(member, Some(machineNameOrIp))
		} else {
			// Try fetching machine by alias
			machineAliasMap.get(machineNameOrIp)
		}

		originalSender ! result
	}

	/**
	 * Retrives the alias for a machine with a certain IP and a port, if the alias exists.
	 * @param ip The ip of the machine to look up
	 * @param port The port of the machine to look up
	 * @return Some(alias) if the machine exists and has an alias, None if the machine is
	 *         not found, the port does not match the machine or the machine does not have an alias.
	 */
	def getMachineAlias(ip: String, port: Int): Option[String] = {
		machineAliasMap.get(ip) match {
			case None => None
			case Some(m) if m.port == port => m.alias
		}
	}

	/**
	 * Gets a list of machines in the cluster.
	 * This uses the state information directly, as opposed to the method getMachine(machineNameOrIp)
	 * because no alias or IP address is provided to retrieve all machines and the state map
	 * can simply be accessed directly.
	 * @return A list of machines that are currently present in the cluster.
	 */
	def getMachines() {
		val originalSender = sender()

		val machines = cluster.state.members.flatMap(member => {
			memberToMachine(Some(member), None)
		}).toList

		val result: JObject = ("machines" -> JArray(machines.map(_.toJson)))
		originalSender ! result
	}

	/**
	 * Add a machine with a given ip and port to the cluster.
	 * @param json The json object containing the ip and the port of the machine.
	 * @return A JSON object containing the result of the operation.
	 */
	def joinCluster(json: JObject): Future[JObject] = {
		val originalSender = sender()

		// The friendly alias of the machine
		val alias = (json \ "alias").extractOpt[String]
		val ip = (json \ "ip").extract[String]
		val port = (json \ "port").extract[Int]
		val roles = (json \ "role").extract[List[String]]

		// Add it to the alias map so that the alias gets remembered the next time
		if (alias.isDefined) {
			machineAliasMap.put(alias.get, Machine(alias, ip, port, roles, Some("added")))
		}

		val address = Address("akka.tcp", "coral", ip, port)

		log.info(s"""Trying to join node with ${if (alias.isDefined) s"""alias "${alias.get}", """ else ""}"""
			+ s"""IP $ip and port $port to the cluster""")

		cluster.joinSeedNodes(List(address))
		Thread.sleep(1000)

		// Let the cluster monitor know a node joined
		val clusterMonitor = context.actorSelection("/user/root/clusterMonitor")
		clusterMonitor ! RefreshCluster(enabled = true)

		checkAddMachine(json) pipeTo originalSender
	}

	/**
	 * Extract a list of Address objects from a list of seed nodes from
	 * the configuration file.
	 * @param list The list to parse.
	 * @return It returns None if the list is empty or a single element
	 *         fails to parse. Otherwise, it returns Some(list) with Addresses.
	 */
	def extractAddresses(list: List[String]): Option[List[Address]] = {
		Some(list.map(Utils.addressFromPath))
	}

	/**
	 * Adds a list of machines to the cluster. This is equivalent to
	 * adding each individual machine to the cluster, one by one.
	 * @param json A JSON object containing the list of machines to add.
	 * @return A JSON object describing the result of the operation.
	 */
	def addMachines(json: JObject): Future[JObject] = {
		val originalSender = sender()

		val machines = (json \ "machines")

		val result = Future.sequence(machines.children.map(child => {
			joinCluster(child.asInstanceOf[JObject])}))

		checkMultipleAddOrRemove(result, "add machines") pipeTo sender
	}

	/**
	 * Remove a machine from the cluster.
	 * No parameter needed for this method since the request is always
	 * executed on the machine that receives the request.
	 * @return A JSON object containing the result of the operation.
	 */
	def leaveCluster(): Future[JObject] = {
		val originalSender = sender()

		val path = Utils.getFullSelfPath(self)
		val m = Utils.machineFromPath(path)

		log.info(s"Trying to remove the machine with IP ${m.ip} and port ${m.port} from the cluster")
		val address = Utils.addressFromPath(path)

		cluster.leave(address)
		Thread.sleep(3000)

		// Let the cluster monitor know a node left
		val clusterMonitor = context.actorSelection("/user/root/clusterMonitor")
		clusterMonitor ! RefreshCluster(false)

		val memberJson =
			("ip" -> m.ip) ~
			("port" -> m.port) ~
			("status" -> m.status)

		checkRemoveMachine(memberJson) pipeTo originalSender
	}

	/**
	 * Check if a machine was succesfully added to the cluster.
	 * Do this by retrieving the cluster information from the cluster monitor
	 * and checking if the added machine is in the cluster information.
	 * @param memberJson The machine that should have been added to the cluster.
	 * @return A JSON object describing the result of the operation.
	 */
	def checkAddMachine(memberJson: JObject): Future[JObject] = {
		checkResult(
			added = true,
			"add machine",
			"Successfully added machine with IP $ip and port $port to the cluster",
			"Failed to add machine with IP $ip and port $port to the cluster",
			memberJson)
	}

	/**
	 * Check if a machine was succesfully removed from the cluster.
	 * Do this by retrieving the cluster information and checking if this machine
	 * is the only machine in the cluster information.
	 * @param memberJson The machine that should have been removed from the cluster.
	 * @return A JSON object describing the result of the operation.
	 */
	def checkRemoveMachine(memberJson: JObject): Future[JObject] = {
		checkResult(
			added = false,
			"remove machine",
			"Succesfully removed the machine with IP $port and port $port from the cluster",
			"Failed to remove the machine with IP $ip and port $port from the cluster",
			memberJson)
	}

	/**
	 * Check whether adding or removing multiple machines from the cluster succeeded.
	 * @param result The result of the add or remove operations.
	 * @param action The friendly description of the action that was performed.
	 * @return A description whether the multiple add/remove actions succeeded or not.
	 */
	def checkMultipleAddOrRemove(result: Future[List[JObject]], action: String): Future[JObject] = {
		result.map(list => {
			// It is a success if all actions are a success
			val success = list.forall((r: JObject) => {
				(r \ "success").extract[Boolean] == true
			})

			val individualSuccess = list.map((r: JObject) => {
				val ip = (r \ "ip").extract[String]
				val result = (r \ "success").extract[Boolean]
				JField(ip, result)
			})

			("action" -> action) ~
				("success" -> success) ~
				("results" -> individualSuccess)
		})
	}

	/**
	 * Check the result of either a remove or an add machine action.
	 * @param added Whether the machine was added or removed
	 * @param action The friendly name of the action
	 * @param successMessage The message to return and to log when the action succeeded.
	 * @param errorMessage The message to return and to log when the action failed.
	 * @param memberJson The JSON description of the member that was supposed to be added.
	 * @return A JSON object that contains the result of the operation.
	 */
	def checkResult(added: Boolean, action: String, successMessage: String, errorMessage: String,
					memberJson: JObject): Future[JObject] = {
		val clusterMonitor = context.actorSelection("/user/root/clusterMonitor")

		val ip = (memberJson \ "ip").extract[String]
		val port = (memberJson \ "port").extract[Int]
		val successFormatted = successMessage.replace("$ip", ip).replace("$port", port.toString)
		val errorFormatted = errorMessage.replace("$ip", ip).replace("$port", port.toString)

		clusterMonitor.ask(ClusterMonitor.GetClusterInfo()).map(answer => {
			// the cluster information object should contain a node with uniqueAddress equal
			// to the ip address given to this method.
			val present = clusterContainsMember(answer.asInstanceOf[JObject], memberJson)

			val (success, message) = present match {
				case false if !added =>
					// The node is succesfully removed
					log.info(successFormatted)
					(true, successFormatted)
				case true if !added =>
					// The node is still present while it should have been removed
					log.error(errorFormatted)
					(false, errorFormatted)
				case false if added =>
					// The node is not present while it should have been added
					log.error(errorFormatted)
					(false, errorFormatted)
				case true if added =>
					// The node is succesfully added
					log.info(successFormatted)
					(true, successFormatted)
			}

			("action" -> action) ~
			("machine" -> memberJson)
			("success" -> success) ~
			("message" -> message)
		})
	}

	/**
	 * Check if the cluster contains a member with a given definition
	 * @param clusterInfo The cluster info to find the member in
	 * @param memberJson The JSON definition of the member that is checked.
	 * @return True when the member is present in the cluster info, false otherwise.
	 */
	def clusterContainsMember(clusterInfo: JObject, memberJson: JObject): Boolean = {
		val memberList = (clusterInfo \ "members")
		val members: List[ClusterMemberInfo] = memberList.extract[List[ClusterMemberInfo]]

		val member = if (memberJson.findField(_._1 == "status").isDefined) {
			memberJson.extract[Machine]
		} else {
			(memberJson ~ ("status" -> "")).extract[Machine]
		}

		val result = members.exists(m => {
			m.address.ip == member.ip &&
			m.address.port == member.port })

		result
	}

	/**
	 * Converts Member objects to Machine objects.
	 * @param member The member object to convert, if any
	 * @param ipOpt The optional IP address of the member. If provided, it will fill in the alias
	 *              of the machine by finding the machine through this IP address. If not provided, it
	 *              will not fill in an alias.
	 * @return A Machine object containing the alias, the ip, the port and the status of the machine.
	 */
	def memberToMachine(member: Option[Member], ipOpt: Option[String]): Option[Machine] = {
		member.map(m => {
			val alias: Option[String] = ipOpt.flatMap(x => machineAliasMap.find(_._2.ip == x).map(_._1))
			val ip: String = m.uniqueAddress.address.host.getOrElse("")
			val port: Int = m.uniqueAddress.address.port.getOrElse(0)
			val status: String = m.status.toString
			val roles = m.roles.toList

			Machine(alias, ip, port, roles, Some(status))
		})
	}
}