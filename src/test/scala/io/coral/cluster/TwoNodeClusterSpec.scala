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

import java.util.UUID
import akka.actor.{ActorRef, ActorSystem, ActorSelection}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.coral.TestHelper
import io.coral.api.security.AcceptAllUser
import io.coral.api.security.Authenticator.{InvalidationComplete, InvalidationFailed, Invalidate}
import io.coral.cluster.ClusterDistributor.{ResetRoundRobin, InvalidateAllAuthenticators}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.junit.JUnitRunner
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http.{HttpCharsets, ContentType}
import io.coral.api.{CoralConfig, Runtime}
import scala.concurrent.{ExecutionContext, Await, Future}
import scalaj.http._
import scala.concurrent.duration._
import akka.pattern.ask

@RunWith(classOf[JUnitRunner])
class TwoNodeClusterSpec
	extends WordSpecLike
	with BeforeAndAfterAll
	with BeforeAndAfterEach
	with Matchers {
	implicit val formats = org.json4s.DefaultFormats
	val AcceptHeader = RawHeader("Accept", "application/json")
	val ContentTypeHeader = RawHeader("Content-Type", "application/json")
	val JsonApiContentType = ContentType(`application/json`, HttpCharsets.`UTF-8`)
	val headers = Map(
		("Content-Type" -> "application/json"),
		("Accept" -> "application/json"))
	val ownerUUID1 = UUID.randomUUID()
	val projectUUID1 = UUID.randomUUID()
	val runtimeUUID1 = UUID.randomUUID()
	val startTime = System.currentTimeMillis

	implicit val config = new CoralConfig(ConfigFactory.parseString(
		s"""coral.authentication.mode = "accept-all"
		   |coral.cluster.enable = false
		   |akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
			 """.stripMargin)
		.withFallback(ConfigFactory.load()))

	// For cleaning up tables
	implicit var system: ActorSystem = _
	implicit var cassandra: ActorRef = _
	implicit val timeout = Timeout(1.second)
	val nrMachines = 2
	val nrRuntimes = 1
	val nrActors = 2
	val sprayPort1 = "8001"
	val sprayPort2 = "8002"
	val akkaPort1 = "2552"
	val akkaPort2 = "2553"
	val debugHelperAkkaPort = "2555"

	override def beforeAll() {
		// Helper system to contact the cluster and clear tables
		val helperSystem = ConfigFactory.parseString(
			s"""akka.cluster.seed-nodes = ["akka.tcp://coral@127.0.0.1:$akkaPort2"]
			   |akka.remote.netty.tcp.port = $debugHelperAkkaPort
			   |akka.cluster.roles = ["debug-helper"]
			   |akka.actor.provider="akka.cluster.ClusterActorRefProvider"
			   |coral.cluster.enable = true
			 """.stripMargin)
			.withFallback(config.getConfig)

		system = ActorSystem("coral", helperSystem)

		cassandra = TestHelper.createCassandraActor(
			config.coral.cassandra.contactPoints.head.getHostName,
			config.coral.cassandra.port)

		TestHelper.clearAllTables()

		start(sprayPort1, akkaPort1)
		start(sprayPort2, akkaPort2)

		Thread.sleep(3000)
	}

	def clearAndRefresh() {
		clear()
		refresh()
	}

	def clear() {
		deleteAllRuntimes(from = sprayPort1)
		TestHelper.clearAllTables()
	}

	def refresh() {
		val distributor = system.actorSelection(
			s"akka.tcp://coral@127.0.0.1:$akkaPort1/user/root/admin/clusterDistributor")

		val invalidationResult = Await.result(distributor
			.ask(InvalidateAllAuthenticators()), timeout.duration)

		invalidationResult match {
			case InvalidationFailed => fail("Failed to invalidate authenticators.")
			case _ =>
		}
	}

	"A two-node cluster" should {
		"return the same cluster info no matter which node it is asked on" in {
			val actualOnNode1 = getClusterInfo(from = sprayPort1)
			val expectedInfo = expectedClusterInfo(akkaPort1, List(akkaPort2),
				nrMachines = 2, nrRuntimes = 0, nrActors = 0)

			assert(actualOnNode1 == expectedInfo)

			val actualOnNode2 = getClusterInfo(from = sprayPort2)
			assert(actualOnNode2 == expectedInfo)
		}

		"post a runtime to one node and request it on the other" in {
			val runtime2: Runtime = createRuntimeDefinition("runtime2",
				on = sprayPort1, owner = AcceptAllUser.uuid.toString)
			addRuntime(runtime2, on = sprayPort1)

			Thread.sleep(500)

			val info: JObject = getRuntimeInfo("runtime2", sprayPort2)
			val actual = (info \ "json").extract[JObject]
			val expected = (runtime2.toJson \ "json").extract[JObject]

			assert(actual == expected)
		}

		"Run the supporting actors on each node" in {
			Thread.sleep(1000)

			implicit val clusterInfo = getClusterInfo(sprayPort1)
			import scala.concurrent.ExecutionContext.Implicits.global

			// These actors should all run on each node in the cluster
			assert(onEachNode("/user/root"))
			assert(onEachNode("/user/root/clusterMonitor"))
			assert(onEachNode("/user/root/cassandra"))
			assert(onEachNode("/user/root/api"))
			assert(onEachNode("/user/root/admin"))
			assert(onEachNode("/user/root/admin/clusterDistributor"))
			assert(onEachNode("/user/root/authenticator"))
			assert(onEachNode("/user/root/authenticator/permissionHandler"))
			assert(onEachNode("/user/root/machineAdmin"))
		}

		"join a machine to a cluster and remove it" in {
			clearAndRefresh()

			// TODO: Add a runtime on it and make sure it continues
			// val runtime = createRuntimeDefinition("runtime1",
			//	on = sprayPort1, owner = AcceptAllUser.uuid.toString)
			// addRuntime(runtime, sprayPort1)
			// Thread.sleep(3000)

			val sprayPort3 = "8003"
			val akkaPort3 = "2554"
			// Keep the node separate at first, default is to join node with port 2551.
			// The node is its own seed node.
			start(sprayPort3, akkaPort3, cluster = false)
			Thread.sleep(3000)

			val expectedBefore = expectedClusterInfo(
				akkaPort1, List(akkaPort2),
				nrMachines, 0, 0)

			val actualNode1Before = getClusterInfo(from = sprayPort1)

			assert(actualNode1Before == expectedBefore)

			/** Join the cluster here */
			joinCluster(from = sprayPort3, to = akkaPort2)
			Thread.sleep(3000)

			val actualNode1joined = getClusterInfo(from = sprayPort1)
			val actualNode2joined = getClusterInfo(from = sprayPort2)
			val actualNode3joined = getClusterInfo(from = sprayPort3)

			val expectedAfterJoin = expectedClusterInfo(
				akkaPort1, List(akkaPort2, akkaPort3),
				nrMachines + 1, 0, 0)

			assert(actualNode1joined == expectedAfterJoin)
			assert(actualNode2joined == expectedAfterJoin)
			assert(actualNode3joined == expectedAfterJoin)

			/** Leave the cluster here **/
			leaveCluster(from = sprayPort3, to = akkaPort2)
			Thread.sleep(10000)

			val expectedAfterLeave = expectedClusterInfo(
				akkaPort1, List(akkaPort2),
				nrMachines, 0, 0)

			val actualNode1left = getClusterInfo(from = sprayPort1)
			val actualNode2left = getClusterInfo(from = sprayPort2)
			val actualNode3left = getClusterInfo(from = sprayPort3)

			val expectedNode3AfterLeave: JObject =
				("success" -> false) ~
				("reason" -> "This node has been removed from the cluster.")

			assert(actualNode1left == expectedAfterLeave)
			assert(actualNode2left == expectedAfterLeave)
			assert(actualNode3left == expectedNode3AfterLeave)
		}

		"run a runtime on a predefined node as specified in the runtime definition" in {
			clearAndRefresh()

			createAndCheckAliveOn(
				"predefined",
			    "coral-distribution1",
				// Distribute on 127.0.0.1:2552
				"127.0.0.1", akkaPort1,
				// The request will be sent to HTTP port 8001
				sprayPort1,
				// It should be alive on akka port 2552
				List(akkaPort1),
				// It should be dead on akka port 2553
				List(akkaPort2),
				clean = true)
		}

		"run a runtime with an invalid distribution mode on the first node" in {
			clearAndRefresh()

			createAndCheckAliveOn(
				"invalid",
				"coral-distribution1",
				"127.0.0.1",
				akkaPort1,
				sprayPort1,
				List(akkaPort1),
				List(akkaPort2),
				clean = true)
		}

		"run a runtime with a distribution with a nonexisting ip address on the first node" in {
			clearAndRefresh()

			createAndCheckAliveOn(
				"invalid",
				"coral-distribution1",
				"1.2.3.4",
				akkaPort1,
				sprayPort1,
				List(akkaPort1),
				List(akkaPort2),
				clean = true)
		}

		"run a runtime with a local runtime distribution on the local machine" in {
			clearAndRefresh()

			createAndCheckAliveOn(
				"local",
				"coral-distribution1",
				sprayPort1,
				List(akkaPort1),
				List(akkaPort2),
				clean = true)

			createAndCheckAliveOn(
				"local",
				"coral-distribution2",
				sprayPort2,
				List(akkaPort2),
				List(akkaPort1),
				clean = false)
		}

		"run a runtime with a round-robin runtime distribution" in {
			clearAndRefresh()

			val distributor = system.actorSelection(
				s"akka.tcp://coral@127.0.0.1:$akkaPort1/user/root/admin/clusterDistributor")
			distributor ! ResetRoundRobin()

			createAndCheckAliveOn(
				"round-robin",
				"coral-distribution3",
				"127.0.0.1",
				akkaPort1,
				sprayPort1,
				List(akkaPort1),
				List(akkaPort2),
				clean = true)

			createAndCheckAliveOn(
				"round-robin",
				"coral-distribution4",
				"127.0.0.1",
				akkaPort1,
				sprayPort1,
				List(akkaPort2),
				List(akkaPort1),
				clean = false)

			createAndCheckAliveOn(
				"round-robin",
				"coral-distribution5",
				"127.0.0.1",
				akkaPort1,
				sprayPort1,
				List(akkaPort1),
				List(akkaPort2),
				clean = false)
		}

		"run a runtime with a least-nr-actors runtime distribution" in {
			/*
			createAndCheckAliveOn(
				"least-nr-actors",
				"coral-distribution6",
				"127.0.0.1",
				akkaPort1,
				sprayPort1,
				List(akkaPort1),
				List(akkaPort2),
				clean = true)

			// akkaPort1 now contains 2 actors
			//createRuntimeWithNActors(10, "127.0.0.1", akkaPort2)

			// akkaPort1 now contains 2 actors and akkaPort2 10 actors

			// This should be assigned to akkaPort1
			createAndCheckAliveOn(
				"least-nr-actors",
				"coral-distribution7",
				sprayPort2,
				List(akkaPort1),
				List(akkaPort2),
				clean = false)
			*/
		}

		"run a runtime with a least-nr-runtimes runtime distribution" in {

		}

		"run a runtime with a least-busy runtime distribution" in {

		}

		"run a runtime with a random runtime distribution" in {

		}

		"take down a node without causing loss of messages" in {
			// Start runtime1 on node1
			// Start runtime2 on node2
			// Kill node1
			// After x seconds, runtime1 and runtime2 should be running on node2
			// Output should be 100% complete, no loss of messages
		}

		"update the AuthorizationActor on all nodes in the cluster" in {

		}

		"send to and receive from a remote node" in {

		}

		"take down a node with a partial runtime on it without losing messages" in {

		}

		"request to create a runtime from one node but start it on another" in {

		}

		"Scale up the cluster using the REST interface" in {

		}

		"Scale down the cluster using the REST interface" in {

		}

		"Do not take down the last running node" in {

		}

		"Continue runtimes running on a node that has crashed on another node" in {

		}

		"Properly distribute runtimes in a round-robin fashion" in {

		}

		"Properly assign a runtime to a predefined machine" in {

		}

		"Properly assign a new runtime to the node with the least number of runtimes" in {

		}

		"Properly assign a new runtime to the node with the least number of actors" in {

		}

		"Randomly assign a runtime to a node" in {

		}

		"Properly assign a new runtime to the least busy node" in {

		}
	}

	def start(sprayPort: String, akkaPort: String) {
		start(sprayPort, akkaPort, cluster = true)
	}

	def start(sprayPort: String, akkaPort: String, cluster: Boolean) {
		val args = Array(
			"start",
			"-ai", "0.0.0.0",
			"-p", sprayPort.toString,
			"-ah", "127.0.0.1",
			"-ap", akkaPort.toString,
			"-am", "accept-all",
			"-ccp", config.coral.cassandra.contactPoints.head.getHostName,
			"-cp", config.coral.cassandra.port.toString,
			"-k", "coral",
			"-ll", "INFO"
		)

		val finalArgs = if (cluster) {
			args ++ Array("-sn", s"akka.tcp://coral@127.0.0.1:$akkaPort1")
		} else {
			args ++ Array("-nc")
		}

		io.coral.api.Boot.main(finalArgs)
	}

	def createRuntimeDefinition(name: String, on: String): Runtime = {
		createRuntimeDefinition(name, "coral", on)
	}

	def createRuntimeDefinition(name: String, owner: String, on: String): Runtime = {
		val uniqueName = owner + "-" + name

		val json = parse(s"""{
			|"name": "$name",
			|"owner": "$owner",
			|"projectid": "${projectUUID1.toString}",
			|"actors": [{
			|		"name": "generator1",
			|		"type": "generator",
			|		"params": {
			|			"format": {
			|				"field1": "N(100, 10)"
			|			}, "timer": {
			|			   "rate": 20,
			|			   "delay": 0
			|			}
			|		}
			|	}, {
			|		"name": "log1",
			|		"type": "log",
			|		"params": {
			|			"file": "/tmp/runtime1.log"
			|		}
			|	}
			|], "links": [{ "from": "generator1", "to": "log1"}]
			|}""".stripMargin).asInstanceOf[JObject]

		Runtime(runtimeUUID1, ownerUUID1, name, uniqueName,
			s"akka.tcp://127.0.0.1:$on/user/root/admin",
			0, Some(projectUUID1), json, None, startTime)
	}

	def addRuntime(runtime: Runtime, on: String): JObject = {
		val json = compact(render(runtime.toJson() \ "json"))
		val answer = Http(s"http://localhost:$on/api/runtimes")
			.postData(json).headers(headers).asString.body
		parse(answer).asInstanceOf[JObject]
	}

	def getClusterInfo(from: String): JObject = {
		val answer = Http(s"http://localhost:$from/api/platform/stats").headers(headers).asString.body
		parse(answer).asInstanceOf[JObject]
	}

	def joinCluster(from: String, to: String): JObject = {
		// Cannot use s"" here because scalaj.http won't allow it (?)
		val data = """{ "action": "join", "ip": "127.0.0.1", "port": """ + to + """}"""
		val url = "http://127.0.0.1:" + from + "/api/platform/cluster"
		val answer = Http(url).postData(data).headers(headers).asString.body
		parse(answer).asInstanceOf[JObject]
	}

	def leaveCluster(from: String, to: String): JObject = {
		val data = """{ "action": "leave" }"""
		val url = "http://127.0.0.1:" + from + "/api/platform/cluster"
		val answer = Http(url).postData(data).headers(headers).asString.body
		parse(answer).asInstanceOf[JObject]
	}

	def getRuntimeInfo(runtimeName: String, from: String): JObject = {
		val url = s"http://127.0.0.1:$from/api/runtimes/$runtimeName"
		val answer = Http(url).headers(headers).asString.body
		parse(answer).asInstanceOf[JObject]
	}

	def startRuntime(runtimeName: String, from: String): JObject = {
		val url = s"http://127.0.0.1:$from/api/runtimes/$runtimeName"
		val json = """{ "status": "start" } """
		val answer = Http(url).postData(json).method("PATCH").headers(headers).asString.body
		val result = parse(answer).asInstanceOf[JObject]
		result
	}

	def deleteAllRuntimes(from: String): JObject = {
		val url = s"http://127.0.0.1:$from/api/runtimes"
		val answer = Http(url).method("DELETE").headers(headers).asString.body
		val result = parse(answer).asInstanceOf[JObject]
		result
	}

	def expectedClusterInfo(leaderNettyPort: String, otherNettyPorts: List[String],
							nrMachines: Int, nrRuntimes: Int, nrActors: Int): JObject = {
		// Also add the debug helper system
		val members = otherNettyPorts ++ List("2555", leaderNettyPort)

		val result = s"""{
			 | "totalMachines": $nrMachines,
			 | "totalRuntimes": $nrRuntimes,
			 | "totalActors": $nrActors,
			 | "totalMessages": 0,
			 | "totalExceptions": 0,
			 | "counters": {
			 |   "total": {
			 |
			 |   }
			 | },
			 | "cluster": {
			 |   "leader": "akka.tcp://coral@127.0.0.1:$leaderNettyPort",
			 |   "roles": [],
			 |   "members":[${createMembers(members)}]
			 | }
			 |}""".stripMargin

		parse(result).asInstanceOf[JObject]
	}

	def createMembers(ports: List[String]): String = {
		def getMemberDef(port: String) = s"""{
			| "address": {
			|   "fullAddress": "akka.tcp://coral@127.0.0.1:$port",
			|   "protocol": "akka.tcp",
			|   "system": "coral",
			|   "ip": "127.0.0.1",
			|   "port": $port
			| },
			| "status": "Up",
			| "roles": []
			|}""".stripMargin

		ports.filter(_ != debugHelperAkkaPort)
			.distinct.sorted
			.map(port => getMemberDef(port)).mkString(",")
	}

	def equalWithoutCounters(actualWithCounters: JObject, expected: JObject) = {
		// Remove the totalMessages and totalExceptions field from the
		// actual JSON because these can vary between runs.
		val actual = actualWithCounters.removeField(_._1 == "totalMessages")
			.removeField(_._1 == "totalExceptions")

		assert(actual == expected)
	}

	def onEachNode(path: String)(implicit clusterInfo: JObject, system: ActorSystem, ec: ExecutionContext): Boolean = {
		implicit val timeout = Timeout(5.seconds)
		val members = (clusterInfo \ "cluster" \ "members").extract[JArray]

		// Exclude the debug helper
		val addresses = members.arr.filter(j =>
			!(j \ "roles").extract[List[String]].contains("debug-helper"))
			.map(m => (m \ "address" \ "fullAddress").extract[String])

		val selections: List[ActorSelection] = addresses.map(a => system.actorSelection(a + path))

		val list = Await.result(Future.sequence(
			selections.map(s => s.resolveOne())), timeout.duration)

		list.size == selections.size
	}

	/**
	 * Creates a new runtime and checks whether it is actually alive
	 * on the node that it should be assigned to, according to the
	 * distribution strategy.
	 * @param mode The distribution mode as used in
	 *             ClusterDistributor.distribute
	 * @param ip The Akka IP address of the machine that will be added.
	 * @param port The Akka port of the machine that will be added.
	 *             This is different from the spray port to which
	 *             the API call will be sent.
	 * @param createOn The spray port to which the HTTP API call will
	 *                 be sent. This port is different than the ports
	 *                 used for Akka.
	 * @param aliveOn The Akka ports on which the runtime should be
	 *                alive. This will query the akka actors directly
	 *                and bypass the API.
	 * @param deadOn The Akka ports on which the runtime should not live.
	 * @param clean Whether previous runtimes should be deleted or not.
	 */
	def createAndCheckAliveOn(mode: String,
							  // The name of the runtime to create
							  runtimeName: String,
							  // The ip address of the predefined machine
							  ip: Option[String],
							  // The akka port of the predefined machine
							  port: Option[String],
							  // The spray port to which the request is sent
							  createOn: String,
							  // The akka ports on which the runtime should live
							  aliveOn: List[String],
							  // The akka ports on which the runtime does not live
							  deadOn: List[String],
							  // Whether previous runtimes will be deleted
							  clean: Boolean = true) {
		if (clean) {
			clearAndRefresh()
		} else {
			refresh()
		}

		val runtime = TestHelper.createRuntimeWithDistribution(
			runtimeName, runtimeUUID1, ownerUUID1, projectUUID1,
			mode, ip, port)

		val answer = addRuntime(runtime, on = createOn)

		aliveOn.foreach(port => {
			val actor = system.actorSelection(
				s"akka.tcp://coral@127.0.0.1:$port/user/root/admin/$runtimeName")
			assert(TestHelper.isAlive(actor))
		})

		deadOn.foreach(port => {
			val actor = system.actorSelection(
				s"akka.tcp://coral@127.0.0.1:$port/user/root/admin/$runtimeName")
			assert(!TestHelper.isAlive(actor))
		})
	}

	def createAndCheckAliveOn(mode: String, runtimeName: String, ip: String, port: String, createOn: String,
							  aliveOn: List[String], deadOn: List[String], clean: Boolean) {
		createAndCheckAliveOn(mode, runtimeName, Some(ip), Some(port), createOn, aliveOn, deadOn, clean)
	}

	def createAndCheckAliveOn(mode: String, runtimeName: String, createOn: String, aliveOn: List[String],
							  deadOn: List[String], clean: Boolean) {
		createAndCheckAliveOn(mode, runtimeName, None, None, createOn, aliveOn, deadOn, clean)
	}
}