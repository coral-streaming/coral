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

package io.coral.api

import java.net.InetSocketAddress
import com.datastax.driver.core.{QueryOptions, Cluster}
import com.typesafe.config.{ConfigFactory, Config}
import scala.collection.JavaConverters._

class CoralConfig(config: Config) {
	import io.coral.api.CoralConfig._
	def getConfig = config

	object coral {
		val conf = config.getConfig("coral")
		val logLevel = conf.getString("log-level")

		object api {
			private val apiConf = conf.getConfig("api")
			val interface: String = apiConf.getString("interface")
			val port: Int = apiConf.getInt("port")
		}

		object authentication {
			private val authConf = conf.getConfig("authentication")
			val mode: String = authConf.getString("mode")

			object ldap {
				private val ldapConf = authConf.getConfig("ldap")
				val host: String = ldapConf.getString("host")
				val port: Int = ldapConf.getInt("port")
				val certificates = if (ldapConf.hasPath("certificates")) {
						ldapConf.getStringList("certificates").asScala
					} else {
						List()
					}
				val bindDN: String = ldapConf.getString("bind-dn")

				object mfa {
					private val mfaConf = ldapConf.getConfig("mfa")
					val enabled = mfaConf.getBoolean("enabled")
					val mobileField = mfaConf.getString("mobile-field")
				}
			}
		}

		object cassandra {
			private val cassDef = conf.getConfig("cassandra")
			val port: Int = cassDef.getInt("port")
			val contactPoints: Seq[InetSocketAddress] =
				getCassandraContactPoints(cassDef.getStringList("contact-points").asScala, port)
			val keyspace: String = cassDef.getString("keyspace")
			val userTable: String = cassDef.getString("user-table")
			val authorizeTable: String = cassDef.getString("authorize-table")
			val runtimeTable: String = cassDef.getString("runtime-table")
			val maxResultSize: Int = cassDef.getInt("max-result-size")
			val user: String = cassDef.getString("user")
			val password: String = cassDef.getString("password")
			val keyspaceAutoCreate: Boolean = cassDef.getBoolean("keyspace-autocreate")

			object persistence {
				private val persistDef = cassDef.getConfig("persistence")
				val journalTable = persistDef.getString("journal-table")
				val snapshotsTable = persistDef.getString("snapshot-table")
				val keyspaceAutoCreate = persistDef.getBoolean("keyspace-autocreate")
			}
		}

		object cluster {
			// enabled is fixed from the start. When this node joined,
			// set the joined flag to true
			var joined = false
			private val clusterDef = conf.getConfig("cluster")
			val enabled: Boolean = clusterDef.getBoolean("enable") || joined
			val seedNodes: List[String] = clusterDef.getStringList("seed-nodes").asScala.toList
		}

		object distributor {
			private val distrConf = conf.getConfig("distributor")
			val mode = distrConf.getString("mode")

			object machine {
				private val machineDef = distrConf.getConfig("machine")
				val alias = machineDef.getString("alias")
				val ip = machineDef.getString("ip")
				val port = machineDef.getInt("port")
			}
		}
	}

	object akka {
		private val akkaConf = config.getConfig("akka")
		object remote {
			private val remoteConf = akkaConf.getConfig("remote")
			val nettyHostname = remoteConf.getString("netty.tcp.hostname")
			val nettyTcpPort = remoteConf.getInt("netty.tcp.port")
		}

		object cluster {
			private val clusterConf = akkaConf.getConfig("cluster")
			val seedNodes = clusterConf.getStringList("seed-nodes").asScala.toList
		}
	}

	val clusterBuilder: Cluster.Builder = Cluster.builder
		.addContactPointsWithPorts(coral.cassandra.contactPoints.asJava)
		.withQueryOptions(new QueryOptions().setFetchSize(coral.cassandra.maxResultSize))
}

object CoralConfig {
	def getCassandraContactPoints(contactPoints: Seq[String], port: Int): Seq[InetSocketAddress] = {
		contactPoints match {
			case null | Nil => throw new IllegalArgumentException("A contact point list cannot be empty.")
			case hosts => hosts map {
				ipWithPort => ipWithPort.split(":") match {
					case Array(host, port) => new InetSocketAddress(host, port.toInt)
					case Array(host) => new InetSocketAddress(host, port)
					case msg => throw new IllegalArgumentException(
						s"A contact point should have the form [host:port] or [host] but was: $msg.")
				}
			}
		}
	}
}