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

/**
 * A helper class for all start/stop options that can
 * be set from the command line. Not all options
 * can be set through the command line but the most
 * important ones can. The rest must be set in the
 * configuration file.
 */
case class CommandLineConfig(/** Start and stop options **/
							 // The main command given
							 cmd: String = "help",
							 // The subcommand given ("user add")
							 subcommand: Option[String] = None,
							 // The configuration file passed, if any
							 config: Option[String] = None,
							 // The port that Spray listens on
							 apiPort: Option[Int] = None,
							 // The interface the API listens to (0.0.0.0 for all)
							 apiInterface: Option[String] = None,
							 // The interface that akka listens on
							 akkaHostname: Option[String] = None,
							 // The port that akka listens on
							 akkaPort: Option[Int] = None,
							 // The akka seed nodes to connect with
							 seedNodes: Option[List[String]] = None,
							 // The authentication mode to use
							 authenticationMode: Option[String] = None,
							 // The cassandra contact points
							 cassandraContactPoints: Option[List[String]] = None,
							 // The cassandra port
							 cassandraPort: Option[Int] = None,
							 // The keyspace to use
							 cassandraKeyspace: Option[String] = None,
							 // When the node should not join a cluster
							 noCluster: Option[Boolean] = None,
							 // The akka seed nodes to connect to
							 clusterSeedNodes: Option[List[String]] = None,
							 // The log level of the application
							 logLevel: Option[String] = None,
							 /** User add and remove options **/
							 uniqueName: Option[String] = None,
							 // The full (first and last) friendly name of the user
							 fullName: Option[String] = None,
							 // The e-mail address of the user
							 email: Option[String] = None,
							 // The mobile phone number of the user
							 mobilePhone: Option[String] = None,
							 // The department of the user
							 department: Option[String] = None) {
}