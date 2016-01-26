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

package io.coral.utils

import java.io.{PrintWriter, StringWriter}
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.{Calendar, UUID}
import akka.actor.{Address, ActorRef}
import io.coral.api.CoralConfig
import io.coral.cluster.Machine
import scala.collection.JavaConverters._
import scala.util.Try

object Utils {
	val dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"

	/**
	 * Converts a stack trace to a string for debugging and logging purposes.
	 * @param e The Throwable to use the stack trace from.
	 * @return The string representation of the stack trace.
	 */
	def stackTraceToString(e: Throwable): String = {
		val stringWriter = new StringWriter()
		val writer = new PrintWriter(stringWriter)
		e.printStackTrace(writer)
		stringWriter.toString()
	}

	/**
	 * Converts a unix epoch timestamp to a friendly time representation.
	 * @param time The time to convert to a string.
	 * @return A friendly representation of the time.
	 */
	def friendlyTime(time: Long): String = {
		val cal = Calendar.getInstance()
		cal.setTimeInMillis(time)
		val format = new SimpleDateFormat(dateTimeFormat)
		format.format(cal.getTime)
	}

	/**
	 * Converts a friendly time representation to a unix epoch timestamp.
	 * @param time The time string to convert to a timestamp.
	 * @return A long representing the time in milliseconds.
	 */
	def timeStampFromFriendlyTime(time: String): Option[Long] = {
		try {
			val format = new SimpleDateFormat(dateTimeFormat)
			Some(format.parse(time).getTime)
		} catch {
			case e: Exception => None
		}
	}

	/**
	 * Create a machine object from a fully qualified cluster-aware
	 * Akka path. Does not fill in an alias for the Machine that is returned.
	 * Fills in an empty status.
	 * @param path The path to get the machine from. Should be a full
	 *             path including IP address and port.
	 * @return A machine with the given IP address and port.
	 */
	def machineFromPath(path: String): Machine = {
		try {
			// example: "akka.tcp://coral@127.0.0.1:2551/user/clusterMonitor"
			val firstColon = path.indexOf(":")
			// protocol is ignored, should always be "akka.tcp"
			val protocol = path.substring(0, firstColon)
			val at = path.indexOf("@")
			// system is ignored, should always be "coral"
			val system = path.substring(firstColon + 3, at)
			val secondColon = path.indexOf(":", firstColon + 1)
			val host = path.substring(at + 1, secondColon)
			val slash = path.indexOf("/", secondColon + 1)
			val end = if (slash == -1) path.length else slash
			val port = path.substring(secondColon + 1, end).toInt

			Machine(None, host, port, List(), None)
		} catch {
			case e: Exception =>
				throw new IllegalArgumentException(path)
		}
	}

	/**
	 * Create an Akka address object from a given path.
	 * @param path The path should have the format of a fully qualified
	 *             cluster-aware Akka path.
	 * @return An address with the given host name and port. The protocol
	 *         and system name are always "akka.tcp" and "coral", respectively,
	 *         and the protocol and system name from the given path are ignored,
	 *         although the extraction algorithm assumes that they are given.
	 *         None if an extraction failure occurs.
	 */
	def addressFromPath(path: String): Address = {
		// example: "akka.tcp://coral@127.0.0.1:2551/user/clusterMonitor"
		val machine = machineFromPath(path)
		Address("akka.tcp", "coral", machine.ip, machine.port)
	}

	/**
	 * Prints the full path (for instance, "akka.tcp://coral@127.0.0.1:2551/user/clusterMonitor")
	 * of a given actor.
	 * @param actor The actor to print the full path for.
	 * @return The full (remoting) path of the actor.
	 */
	def getFullSelfPath(actor: ActorRef): String = {
		val completePath = akka.serialization.Serialization.serializedActorPath(actor)

		if (completePath.contains("#")) {
			completePath.substring(0, completePath.lastIndexOf("#"))
		} else {
			completePath
		}
	}

	/**
	 * Prints a fatal error to the console and then exits the application
	 * @param e The error to print the stack trace and message of.
	 */
	def printFatalError(e: Throwable) {
		val sw = new StringWriter()
		e.printStackTrace(new PrintWriter(sw))
		println(("=" * 42) + " [FATAL ERROR] " + ("=" * 42) + "\n"
			+ sw.toString()
			+ ("=" * 99))
		System.exit(1)
	}

	/**
	 * Try to parse an UUID from a string. If it succeeds, return Some(uuid).
	 * If it fails, return None.
	 * @param possibleUUID A string possibly containing a UUID.
	 */
	def tryUUID(possibleUUID: String): Option[UUID] = {
		try {
			Some(UUID.fromString(possibleUUID))
		} catch {
			case e: IllegalArgumentException => None
		}
	}

	/**
	 * Try an UUID based on an Option of a string.
	 * @param possibleUUID The UUID to try.
	 * @return None if possibleUUID is none, tryUUID(possibleUUID) otherwise.
	 */
	def tryUUID(possibleUUID: Option[String]): Option[UUID] = {
		possibleUUID match {
			case None => None
			case Some(u) => tryUUID(u)
		}
	}

	/**
	 * Set environment variables for the akka persistence for cassandra plugin.
	 * There is no way to dynamically configure the akka peristence plugin
	 * for Cassandra with a list of seed nodes since the configuration object
	 * is never passed explicitly to the akka library. Therefore, override it
	 * with system parameters. These are read from the akka persistence plugin.
	 * @param config The CoralConfig to get the Cassandra contact points from.
	 */
	def setPersistenceSystemProps(config: CoralConfig) {
		val persistenceNodes = config.getConfig
			.getStringList("coral.cassandra.contact-points").asScala.toList

		persistenceNodes.indices.foreach(i => {
			System.setProperty(s"coral.cassandra.contact-points.$i", persistenceNodes(i))
		})
	}

	/**
	 * Cleans a contact point list. Makes sure that users did not enter
	 * "192.168.0.1:9042" as IP address. In this case, the port is ignored.
	 * The IP address is parsed and if it fails, an exception is thrown.
	 * @param list The list of addresses.
	 * @return A list of addresses without ports, if provided.
	 */
	def cleanContactPointList(list: List[String]): List[String] = {
		list.map(cp => {
			val ip = if (cp.contains(":")) cp.split(":")(0) else cp

			if (!Try(InetAddress.getByName(ip)).isSuccess) {
				throw new IllegalArgumentException(ip)
			}

			ip
		})
	}
}
