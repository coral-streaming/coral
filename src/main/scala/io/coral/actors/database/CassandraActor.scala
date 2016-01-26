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

package io.coral.actors.database

import akka.actor.{ActorLogging, Props}
import com.datastax.driver.core._
import io.coral.actors.database.CassandraActor.QueryResult
import io.coral.api.CoralConfig
import io.coral.utils.Utils
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import io.coral.actors.{SimpleEmitTrigger, CoralActor}
import scala.collection.mutable.{ListBuffer => mList}

object CassandraActor {
	implicit val formats = org.json4s.DefaultFormats

	case class QueryResult(result: Option[ResultSet], lastQuery: String, lastError: String)

	def getParams(json: JValue) = {
		for {
			seeds <- (json \ "params" \ "seeds").extractOpt[List[String]]
			keyspace <- (json \ "params" \ "keyspace").extractOpt[String]
		} yield {
			val port = (json \ "params" \ "port").extractOpt[Int]
			val user = (json \ "params" \ "user").extractOpt[String]
			val password = (json \ "params" \ "password").extractOpt[String]

			(seeds, port, keyspace, user, password)
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[CassandraActor], json))
	}

	def fromConfig(config: CoralConfig): Props = {
		val seeds = config.coral.cassandra.contactPoints.map(_.getHostName)
		val keyspace = config.coral.cassandra.keyspace
		val port = config.coral.cassandra.port
		val json = parse(s"""{
		    | "params": {
			|   "seeds": [${seeds.mkString("\"", "\",\"", "\"")}],
			|   "keyspace": "$keyspace",
			|   "port": $port
			| }
			}""".stripMargin).asInstanceOf[JObject]
		Props(new CassandraActor(json))
	}
}

class CassandraActor(json: JObject) extends CoralActor(json)
	with CassandraHelper
	with SimpleEmitTrigger
	with ActorLogging {
	var (seeds, port, keyspace, user, password) = CassandraActor.getParams(json).get
	implicit val config = new CoralConfig(context.system.settings.config)

	override def preStart() {
		log.info(s"""Starting up CassandraActor with seeds ${seeds.toString}, port $port and keyspace "$keyspace"""")
		ensureConnection(seeds, port, keyspace, user, password)
	}

	var cluster: Cluster = _
	var session: Session = _
	var schema: JValue = _

	override def state = Map(
		("connected", render(session != null && !session.isClosed)),
		("keyspace", render(keyspace)),
		("schema", render(getSchema(session, keyspace)))
	)

	override def simpleEmitTrigger(json: JObject) = {
		ensureConnection(seeds, port, keyspace, user, password)

		// If no query field is present, this actor ignores the message
		if ((json \ "query").extractOpt[String].isDefined) {
			val queryResult = try {
				val query = (json \ "query").extractOpt[String].get.trim()

				val singleLineQuery = query.replace("\n", "")
				log.info( s"""Executing query "$singleLineQuery"""")

				// Since there is no way of using the AST from the query,
				// we use text parsing instead
				val result = if (query.startsWith("use keyspace")) {
					log.info("Changing keyspace, updating schema")
					keyspace = query.substring(13, query.length - 1)
					ensureConnection(seeds, port, keyspace, user, password)
					getSchema(session, keyspace)
					None
				} else {
					val data = session.execute(query)

					if (query.startsWith("select")) {
						Some(data)
					} else {
						None
					}
				}

				QueryResult(result, query, "")
			} catch {
				case e: Exception =>
					log.error(e.getMessage)
					QueryResult(None, (json \ "query").extractOrElse[String](""), e.getMessage)
			}

			Some(determineResult(queryResult))
		} else {
			log.warning("Ignoring message without query field")
			None
		}
	}

	def determineResult(queryResult: QueryResult): JValue = {
		val result = queryResult.result match {
			case Some(data) =>
				// Actual data returned
				render(("seeds" -> JArray(seeds.map(JString))) ~
					("query" -> queryResult.lastQuery) ~
					("success" -> true) ~
					renderResultSet(data))
			case None if (queryResult.lastError != "") =>
				// Error, and therefore no results
				render(("query" -> queryResult.lastQuery) ~
					("success" -> false) ~
					("error" -> queryResult.lastError))
			case None =>
				// No error, just no results
				render(("query" -> queryResult.lastQuery) ~
					("success" -> true))
			case _ =>
				JNothing
		}

		result
	}

	/**
	 * Ensure a connection to Cassandra. If already connected,
	 * do nothing. If the given keyspace is not the same as the
	 * keyspace of the session, reconnect to the new keyspace.
	 * @param seeds The seed nodes to connect to
	 * @param port The port to use, None to use the default port
	 * @param keyspace The keyspace to connect to
	 * @param user The user to login with, if any
	 * @param password The password to login with, if any
	 */
	def ensureConnection(seeds: List[String], port: Option[Int], keyspace: String,
						 user: Option[String], password: Option[String]) {
		// No need to connect if already having a valid session object
		if (session == null || session.isClosed || session.getLoggedKeyspace != keyspace) {
			log.info("CassandraActor not yet connected. Connecting now...")
			cluster = createCluster(seeds, port, user, password)

			try {
				session = cluster.connect(keyspace)
				schema = getSchema(session, keyspace)
			} catch {
				case e: Exception =>
					Utils.printFatalError(e)
			}
		}
	}
}

trait CassandraHelper {
	/**
	 * Returns a list of all table names currently in the keyspace
	 * Returns a list of all table names currently in the keyspace
	 * @param session The session object to connect with
	 * @param keyspace The name of the keyspace
	 * @return A list of all table names in the keyspace
	 */
	def getTables(session: Session, keyspace: String): List[String] = {
		val result = scala.collection.mutable.ListBuffer.empty[String]

		val tablemetadata = session.getCluster.getMetadata
			.getKeyspace(keyspace).getTables.iterator()

		while (tablemetadata.hasNext) {
			result += tablemetadata.next().getName
		}

		result.toList
	}

	/**
	 * Method that returns a JSON representation of a schema
	 * from a Cassandra keyspace.
	 * @param session The session object to get the information with
	 * @param keyspace The keyspace to get the schema from
	 * @return A JSON representation of the schema of that keyspace
	 */
	def getSchema(session: Session, keyspace: String): JObject = {
		var result = mList.empty[(String, JValue)]
		val tables = session.getCluster.getMetadata.getKeyspace(keyspace).getTables
		val tableIt = tables.iterator()

		while (tableIt.hasNext) {
			var columns = mList.empty[(String, JValue)]
			val table = tableIt.next()
			val columnIt = table.getColumns.iterator()

			while (columnIt.hasNext) {
				val column = columnIt.next()
				val name = column.getName
				val datatype = column.getType.getName.toString
				columns += ((name -> datatype))
			}

			// Sort the column names alphabetically
			result += ((table.getName, JObject(columns.sortBy(_._1): _*)))
		}

		// Sort the table names alphabetically
		JObject(result.sortBy(_._1): _*)
	}

	/**
	 * Method that creates a JObject from a Cassandra ResultSet.
	 * @param rs The ResultSet
	 * @return A JObject representation of the ResultSet.
	 */
	def renderResultSet(rs: ResultSet): JObject = {
		val dataIt = rs.iterator()
		val columns = rs.getColumnDefinitions.asList
		var result = mList.empty[JObject]

		while (dataIt.hasNext) {
			var row = JObject()
			val r = dataIt.next

			for (i <- 0 until columns.size) {
				val col = columns.get(i)

				// See http://www.datastax.com/documentation/developer
				//      /java-driver/1.0/java-driver/reference/javaClass2Cql3Datatypes_r.html
				// This list is not complete

				val value = if (r.isNull(i)) {
					null
				} else {
					col.getType.getName.toString match {
						case "ascii" => JString(r.getString(i))
						case "bigint" => JInt(r.getLong(i))
						case "boolean" => JBool(r.getBool(i))
						case "int" => JInt(r.getInt(i))
						case "decimal" => JDecimal(r.getDecimal(i))
						case "double" => JDouble(r.getDouble(i))
						case "float" => JDouble(r.getFloat(i))
						case "text" => JString(r.getString(i))
						case "varchar" => JString(r.getString(i))
						case "varint" => JInt(r.getLong(i))
						case "timestamp" => JInt(r.getDate(i).getTime)
						case "uuid" => JString(r.getUUID(i).toString)
						case other => throw new IllegalArgumentException(other)
					}
				}

				row = row ~ (col.getName -> value)
			}

			result += row
		}

		("data" -> JArray(result.toList))
	}

	/**
	 * Connect to a Cassandra cluster.
	 * @param seeds The seed nodes to connect to
	 * @param port The port, if any. If no port is specified, port 9042 will be used.
	 * @param user The user. If no user is specfied, no credentials are set.
	 *             Login will then only succeed if AllowAllAuthenticator is set for Cassandra.
	 * @param password The password to log in with
	 * @return A Cassandra Cluster object with the provided settings.
	 */
	def createCluster(seeds: List[String], port: Option[Int],
					  user: Option[String], password: Option[String])(implicit config: CoralConfig): Cluster = {
		val builder = Cluster.builder()
		if (port.isDefined) {
			builder.withPort(port.get)
		}

		builder.withCredentials(
			config.coral.cassandra.user,
			config.coral.cassandra.password)

		builder.addContactPoints(seeds: _*)

		if (user.isDefined && password.isDefined) {
			builder.withCredentials(user.get, password.get)
		}

		builder.build()
	}
}