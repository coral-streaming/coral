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

package io.coral

import java.util.UUID
import akka.actor._
import akka.testkit.TestActorRef
import akka.util.Timeout
import io.coral.actors.CoralActor.Shunt
import io.coral.actors.RootActor
import io.coral.actors.RootActor._
import io.coral.actors.RuntimeAdminActor.CreateRuntime
import io.coral.actors.database.{DatabaseStatements, CassandraActor}
import io.coral.api.security.Authenticator.Invalidate
import io.coral.api.{Runtime, CoralConfig}
import io.coral.api.security._
import io.coral.utils.Utils
import org.json4s.JsonAST.JObject
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._
import akka.pattern.ask

object TestHelper {
	implicit val formats = org.json4s.DefaultFormats
	implicit val timeout = Timeout(5.seconds)

	def auth(owner: String) = AuthInfo(Left(User(UUID.randomUUID(), "", None, "", owner, None, "", 0, None)), List())

	def json(user1UUID: UUID): String = json("runtime1", user1UUID.toString)
	def json(user1UUID: UUID, name: String): String = json(name, user1UUID.toString)
	def json(name: String, owner: String): String = json(name, owner.toString, "/tmp/coral.log", "N(100, 10)")
	def json(name: String, owner: String, file: String): String = json(name, owner.toString, file, "N(100, 10)")
	def json(name: String, owner: String, file: String, field1: String) = s"""{
			| "name": "$name",
			| "owner": "$owner",
			| "projectid": "${UUID.randomUUID().toString}",
			| "actors": [{
			|   "name": "generator1",
			|   "type": "generator",
			|   "params": {
			|		"format": {
			|			"field1": "$field1"
			|		}, "timer": {
			|			"rate": 10,
			|			"times": 10,
			|			"delay": 0
			|		}
			|	}}, {
			|	"name": "log1",
			|	"type": "log",
			|	"params": {
			|		"file": "$file"
			|	}}], "links": [
			| 	{ "from": "generator1", "to": "log1" }]
		}""".stripMargin

	def isAlive(actor: ActorSelection): Boolean = {
		try {
			val result = Await.result(actor.resolveOne(), Timeout(1.second).duration)
			true
		} catch {
			case e: Exception => {
				false
			}
		}
	}

	/**
	 * Generic helper function for tests that need to have a clean system
	 * prepared before they run. Cleans up the root actor and the admin actor
	 * and clears the database.
	 */
	def beforeEach()(implicit system: ActorSystem, cassandra: ActorRef,
					 config: CoralConfig, injector: scaldi.Injector, ec: ExecutionContext) {
		val root = system.actorSelection("/user/root")
		val admin = system.actorSelection("/user/root/admin")
		val authenticator = system.actorSelection("/user/root/authenticator")

		assert(TestHelper.isAlive(root))
		assert(TestHelper.isAlive(admin))

		TestHelper.clearTables(cassandra, config.coral.cassandra.keyspace, List(
			config.coral.cassandra.persistence.journalTable,
			config.coral.cassandra.persistence.snapshotsTable,
			config.coral.cassandra.authorizeTable,
			config.coral.cassandra.runtimeTable))

		authenticator ! Invalidate()
		Thread.sleep(1000)
	}

	def createStandardRuntime(runtimeName: String, owner: String, admin: ActorSelection): UUID = {
		val jsonDef = parse(json(runtimeName, owner)).asInstanceOf[JObject]
		val result = Await.result(admin.ask(CreateRuntime(jsonDef, auth(owner))), timeout.duration).asInstanceOf[JObject]
		val uuid = UUID.fromString((result \ "id").extract[String])
		uuid
	}

	def createStandardRuntime(runtimeName: String, owner: UUID, admin: ActorSelection): UUID = {
		createStandardRuntime(runtimeName, owner.toString, admin)
	}

	def createRuntimeWithDistribution(runtimeName: String, runtimeUUID: UUID, ownerUUID: UUID, projectUUID: UUID,
									  mode: String, ip: Option[String], port: Option[String]): Runtime = {
		val name = runtimeName.split("-")(1)

		val json = parse(s"""{
			|"name": "$name",
			|"owner": "ab12cd",
			|"projectid": "${projectUUID.toString}",
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
			|], "links": [{ "from": "generator1", "to": "log1"}],
			| "distribution": ${createDistribution(mode, ip, port)}
			|}""".stripMargin).asInstanceOf[JObject]

		Runtime(runtimeUUID, ownerUUID, name, runtimeName,
			s"akka.tcp://127.0.0.1:$port/user/root/admin",
			0, Some(projectUUID), json, None, System.currentTimeMillis)
	}

	def createDistribution(mode: String, ip: Option[String], port: Option[String]): String = {
		val distr = ("distribution" -> ("mode" -> mode))

		val result: JObject = if (ip.isDefined && port.isDefined) {
			("mode" -> mode) ~ ("machine" ->
				("ip" -> ip.get) ~ ("port" -> JInt(port.get.toInt)))
		} else {
			("mode" -> mode)
		}

		pretty(render(result))
	}

	def createRuntimeWithNActors(n: Int, runtimeUUID: UUID, ownerUUID: UUID, projectUUID: UUID,
								 mode: String, ip: String, port: String): Runtime = {
		def generateNActors(n: Int): List[JObject] = {
			Range(1, n).inclusive.map(i => {
				s"""{
				  |  "name": "actor$i",
				  |  "type": "generator",
				  |	   "params": {
				  |	     "format": {
				  |		   "field1": "N(100, 10)"
				  |		 }, "timer": {
				  |		   "rate": 20,
				  |		   "delay": 0
				  |		 }
				  |	   }
				  |}
				""".stripMargin
			}).map(parse(_).asInstanceOf[JObject]).toList
		}

		def generateNLinks(n: Int): List[JObject] = {
			Range(1, n - 1).inclusive.map(i => {
				("from" -> s"actor$i") ~ ("to" -> s"actor${i + 1}")
			}).toList
		}

		val actors = generateNActors(n)
		val links = generateNLinks(n)

		val result =
			("name" -> "predefined1") ~
			("owner" -> "ab12cd") ~
			("projectid" -> projectUUID.toString) ~
			("actors" -> JArray(actors)) ~
			("links" -> JArray(links)) ~
			("distribution" -> ("mode" -> mode) ~
				("machine" -> ("ip" -> ip) ~ ("port" -> port)))

		// This runtime actually does not make any sense but it can be created successfully
		Runtime(runtimeUUID, ownerUUID, "predefined1", "ab12cd-predefined1",
			s"akka.tcp://127.0.0.1:$port/user/root/admin",
			0, Some(projectUUID), result, None, System.currentTimeMillis)
	}

	def equalWithoutId(actual: JObject, expected: JObject): Boolean = {
		val id = try {
			Some(UUID.fromString((actual \ "id").extract[String]))
		} catch {
			case e: Exception =>
				None
		}

		if (id.isDefined) {
			val withoutId = actual.removeField(_._1 == "id")
			withoutId == expected
		} else {
			false
		}
	}

	def printComparison(actual: JObject, expected: JObject) = {
		val result = "====== Actual ======\n" +
			pretty(render(actual)) + "\n" +
			"===== Expected =====\n" +
			pretty(render(expected)).trim()

		println(result)
	}

	def equalWithoutCreatedAndId(actual: JObject, expected: JObject): Boolean = {
		// We cannot check for the exact timestamp since we do not know it exactly.
		// Therefore, just check if it is there, remove it, and compare the rest.
		// We also cannot check for the exact id so remove it as well.
		val created = (actual \ "created").extractOpt[String]

		if (created.isDefined) {
			assert(Utils.timeStampFromFriendlyTime(created.get).isDefined)
			equalWithoutId(actual.removeField(_._1 == "created")
				.asInstanceOf[JObject], expected)
		} else {
			false
		}
	}

	def createCassandraActor(ip: String, port: Int)(implicit system: ActorSystem): TestActorRef[CassandraActor] = {
		val json = parse(
			s"""{ "params": {"seeds": ["$ip"],
			   |"port": $port, "keyspace": "system" } }""".stripMargin)

		val props = CassandraActor(json)
		assert(props.isDefined)

		val propsVal = props.get
		TestActorRef[CassandraActor](propsVal, "cassandra")
	}

	def clearAllTables()(implicit system: ActorSystem, config: CoralConfig,
						 cassandra: ActorRef)  = {
		TestHelper.clearTables(cassandra, config.coral.cassandra.keyspace, List(
			config.coral.cassandra.persistence.journalTable,
			config.coral.cassandra.persistence.snapshotsTable,
			config.coral.cassandra.authorizeTable,
			config.coral.cassandra.runtimeTable,
			config.coral.cassandra.userTable))
	}

	def clearTables(cassandra: ActorRef, keyspace: String, tables: List[String])(
		implicit system: ActorSystem, conf: CoralConfig) {

		tables.foreach(table => {
			val stmt = s"""truncate $keyspace.$table;"""
			val query = ("query" -> stmt)
			val answer = Await.result(cassandra.ask(Shunt(query)), timeout.duration).asInstanceOf[JObject]

			if (table != conf.coral.cassandra.persistence.journalTable &&
				table != conf.coral.cassandra.persistence.snapshotsTable) {
				assert((answer \ "success").extract[Boolean] == true)
			}
		})
	}

	def getContentsOfTable(cassandra: TestActorRef[CassandraActor], keyspace: String, table: String)(
		implicit timeout: Timeout): JObject = {
		val query = parse(s"""{ "query": "select * from $keyspace.$table;" }""").asInstanceOf[JObject]
		val answer = Await.result(cassandra.ask(Shunt(query)), timeout.duration).asInstanceOf[JObject]
		answer
	}

	def execute(cassandra: TestActorRef[CassandraActor], script: String) {
		// This is done synchronously on purpose
		val json = parse(script).asInstanceOf[JObject]
		cassandra.underlyingActor.process(json)
	}

	def insertUser(id: UUID, fullName: String, department: String, email: String, uniqueName: String,
				   mobilePhone: String, hashedPassword: String, createdOn: Long, lastLogin: Long)(
		implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]): Unit = {
		val query = s"""{ "query": "insert into ${config.coral.cassandra.keyspace}.${config.coral.cassandra.userTable} (id, fullname,
			   | department, email, uniquename, mobilephone, hashedpassword, createdon, lastlogin) values (
			   | $id, '$fullName', '$department', '$email','$uniqueName', '$mobilePhone',
			   | '$hashedPassword', $createdOn, $lastLogin);" } """.stripMargin
		TestHelper.execute(cassandra, query)
	}

	def getUserUUIDFromUniqueName(authenticator: ActorSelection, uniqueName: String) = {
		Await.result(authenticator.ask(Authenticator.GetUserUUIDFromUniqueName(uniqueName)),
			timeout.duration).asInstanceOf[Option[UUID]].get
	}

	def insertRuntime(id: UUID, owner: UUID, name: String, adminPath: String, status: Int, projectId: UUID,
					  jsonDef: JObject, startedOn: Long)(
		implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {
		val query = s"""{ "query": "insert into ${config.coral.cassandra.keyspace}.${config.coral.cassandra.runtimeTable}
			   |(id, owner, name, adminpath, status, projectid, jsondef, startedon) values
			   |($id, $owner, '$name', '$adminPath', $status, $projectId, '$jsonDef', $startedOn);" }""".stripMargin
		TestHelper.execute(cassandra, query)
	}

	def insertPermission(permissionHandler: ActorSelection, id: UUID,
						 user: UUID, runtime: UUID, method: String, uri: String, allowed: Boolean)(
		implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {

		val jsonStr = s"""{ "user": "${user.toString}",
			   |"id": "${id.toString}",
			   |"runtime": "${runtime.toString}",
			   |"method": "$method",
			   |"uri": "$uri",
			   |"allowed": $allowed }""".stripMargin
		val json = parse(jsonStr).asInstanceOf[JObject]
		Await.result(permissionHandler.ask(AddPermission(json)), timeout.duration)
	}

	def insertPermission(permissionHandler: ActorSelection, p: Permission)(
		implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {
		insertPermission(permissionHandler, p.id, p.user, p.runtime, p.method, p.uri, p.allowed)
	}

	def clearRuntimeTable()(implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {
		clearTable(config.coral.cassandra.runtimeTable)
	}

	def clearPermissionTable()(implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {
		clearTable(config.coral.cassandra.authorizeTable)
	}

	def clearSnapshotTable()(implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {
		clearTable(config.coral.cassandra.persistence.snapshotsTable)
	}

	def clearJournalTable()(implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {
		clearTable(config.coral.cassandra.persistence.journalTable)
	}

	def clearTable(tableName: String)(implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {
		val query = s"""{ "query": "truncate ${config.coral.cassandra.keyspace}.${tableName};" }"""
		TestHelper.execute(cassandra, query)
	}

	def awaitResult(actor: ActorSelection, message: Any): JObject = {
		val future = Await.result(actor.ask(message), timeout.duration)
		val result = Await.result(future.asInstanceOf[Future[JObject]], timeout.duration)
		result
	}

	def awaitResult(actor: ActorRef, message: Any): JObject = {
		Await.result(actor.ask(message), timeout.duration).asInstanceOf[JObject]
	}

	def prepareTables()(implicit config: CoralConfig, cassandra: TestActorRef[CassandraActor]) {
		val scripts = Array(
			//s"""{ "query": "drop keyspace if exists ${config.coral.cassandra.keyspace};" } """,
			//s"""{ "query": "create keyspace ${config.coral.cassandra.keyspace} with replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 };" } """,
			s"""{ "query": "use keyspace ${config.coral.cassandra.keyspace};" } """,
			s"""{ "query": "drop table if exists ${config.coral.cassandra.keyspace}.${config.coral.cassandra.userTable};" } """,
			s"""{ "query": "drop table if exists ${config.coral.cassandra.keyspace}.${config.coral.cassandra.authorizeTable};" } """,
			s"""{ "query": "drop table if exists ${config.coral.cassandra.keyspace}.${config.coral.cassandra.runtimeTable};" } """,
			s"""{ "query": "${DatabaseStatements.createUserTable(config)}" } """,
			s"""{ "query": "${DatabaseStatements.createAuthorizationTable(config)}" } """,
			s"""{ "query": "${DatabaseStatements.createRuntimeTable(config)}" } """)

		scripts.foreach(script => {
			execute(cassandra, script)
		})
	}

	def createAuthenticator(config: CoralConfig)(
			implicit system: ActorSystem, ec: ExecutionContext): TestActorRef[Authenticator] = {
		config.coral.authentication.mode match {
			case "coral" =>
				TestActorRef[Authenticator](new CoralAuthenticator(config), "authenticator")
			case "ldap" =>
				TestActorRef(new LDAPAuthenticator(config), "authenticator")
			case "accept-all" =>
				TestActorRef(new AcceptAllAuthenticator(config), "authenticator")
			case "deny-all" =>
				// Dummy, is not actually used in deny-all mode
				TestActorRef[Authenticator](new CoralAuthenticator(config), "none")
		}
	}

	def validTimestamp(value: JValue): Boolean = {
		val time = value.extractOpt[String]
		assert(time.isDefined)
		val timestamp: Option[Long] = Utils.timeStampFromFriendlyTime(time.get)
		timestamp.isDefined
	}
}