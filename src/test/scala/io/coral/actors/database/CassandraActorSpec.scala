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

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.TestHelper
import io.coral.actors.CoralActor.{Shunt, GetField}
import io.coral.api.CoralConfig
import org.json4s._
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import com.github.t3hnar.bcrypt._
import org.mindrot.jbcrypt.BCrypt
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.{AskTimeoutException, ask}

@RunWith(classOf[JUnitRunner])
class CassandraActorSpec(_system: ActorSystem) extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	implicit val timeout = Timeout(2.seconds)
	val duration = timeout.duration
	def this() = this(ActorSystem("CassandraActorSpec"))
	val config = new CoralConfig(system.settings.config)
	val cassandra = TestHelper.createCassandraActor(
		config.coral.cassandra.contactPoints.head.getHostName,
		config.coral.cassandra.port)

	val keyspace = "CassandraActorSpec"

	val scripts = Array(
		s"""{ "query": "drop keyspace if exists $keyspace;" } """,
		s"""{ "query": "create keyspace $keyspace with replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 };" } """,
		s"""{ "query": "use keyspace $keyspace;" } """,
		s"""{ "query": "drop table if exists test1;" } """,
		s"""{ "query": "drop table if exists test2;" } """,
		s"""{ "query": "create table $keyspace.test1 (
                     col1 int,
                     col2 varchar,
                     col3 boolean,
                     primary key (col1));" } """,
		s"""{ "query": "create table $keyspace.test2 (
                     data1 int,
                     data2 varchar,
                     data3 boolean,
                     primary key (data1));" } """,
		s"""{ "query": "insert into $keyspace.test1 (col1, col2, col3) values (1, 'blabla', true);" } """,
		s"""{ "query": "insert into $keyspace.test1 (col1, col2, col3) values (2, 'abc', false);" } """,
		s"""{ "query": "insert into $keyspace.test1 (col1, col2, col3) values (3, 'stuff', true);" } """,
		s"""{ "query": "insert into $keyspace.test2 (data1, data2, data3) values (53, 'foo', true);" } """,
		s"""{ "query": "insert into $keyspace.test2 (data1, data2, data3) values (72, 'bar', false);" } """)

	prepareDatabase()

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"A Cassandra actor" should {
		"Return the correct state in JSON" in {
			val actual = Await.result(cassandra.ask(GetField("schema")), timeout.duration)

			val expected = parse(
				"""{ "schema": {
					"test1": {
                  		"col1": "int",
						"col2": "text",
						"col3": "boolean"
					}, "test2": {
						"data1": "int",
						"data2": "text",
						"data3": "boolean"
  					}
				}}""")

			assert(actual == expected)
		}

		"Return a JSON representation of a ResultSet" in {
			val query = parse(s"""{ "query": "select * from $keyspace.test1" } """)
				.asInstanceOf[JObject]
			val actual1 = Await.result(cassandra.ask(Shunt(query)), timeout.duration)
			val actual2 = actual1.asInstanceOf[JObject].removeField(_._1 == "seeds")

			val expected = parse(s"""{
               	"query": "select * from $keyspace.test1",
				"success": true,
               	"data": [{
               		"col1": 1,
					"col2": "blabla",
	 				"col3": true
	  			}, {
	  				"col1": 2,
	   				"col2": "abc",
					"col3": false
	 			}, {
	 				"col1": 3,
	  				"col2": "stuff",
	   				"col3": true
				}]
            }""")

			assert(actual2 == expected)
		}

		"Return false when selecting from nonexisting table and keyspace" in {
			val query = parse(s"""{ "query": "select * from doesnotexist.doesnotexist" } """)
				.asInstanceOf[JObject]
			val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)

			val expected = parse("""{
                "query": "select * from doesnotexist.doesnotexist",
                "success": false,
                "error": "Keyspace doesnotexist does not exist"
			}""")

			assert(actual == expected)
		}

		"Return false when selecting from a nonexisting table in a nonexisting keyspace" in {
			val query = parse(s"""{ "query": "select * from $keyspace.doesnotexist" } """)
				.asInstanceOf[JObject]
			val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)

			val expected = parse(
				s"""{
                "query": "select * from $keyspace.doesnotexist",
                "success": false,
                "error": "unconfigured columnfamily doesnotexist"
            }""")

			assert(actual == expected)
		}

		"Return false when executing an illegal query" in {
			val query = parse( """{ "query": "this is not a query" } """).asInstanceOf[JObject]
			val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)

			val expected = parse("""{
                "query": "this is not a query",
                "success" false,
                "error": "line 1:0 no viable alternative at input 'this' ([this]...)"
            }""")

			assert(actual == expected)
		}

		"Return data in the correct format" in {
			val insert = parse(
				s"""{ "query": "insert into $keyspace.test1
				  |(col1, col2, col3) values (4, 'test1', true);" } """.stripMargin)
				.asInstanceOf[JObject]

			Await.result(cassandra.ask(Shunt(insert)), timeout.duration)

			val queryString = s"select count(*) from $keyspace.test1 where col1 = 4;"
			val query = s"""{ "query": "$queryString" } """
			val select = parse(query).asInstanceOf[JObject]
			val actual1 = Await.result(cassandra.ask(Shunt(select)), timeout.duration)
			val actual2 = actual1.asInstanceOf[JObject].removeField(_._1 == "seeds")
			val expected = parse(s"""{ "query": "$queryString","success": true, "data": [{ "count": 1 }] }""")

			assert(actual2 == expected)
		}

		"Execute a valid delete query that returns no results" in {
			val queryString = s"delete from $keyspace.test1 where col1 = 4;"
			val query = parse( s"""{ "query": "$queryString" } """).asInstanceOf[JObject]
			val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)
			val expected = parse( s"""{ "query": "$queryString", "success": true }""".stripMargin)
			assert(actual == expected)
		}

		"Return an empty list on select without result" in {
			val queryString = s"select * from $keyspace.test1 where col1 = 4;"
			val query = parse( s"""{ "query": "$queryString" } """).asInstanceOf[JObject]
			val actual1 = Await.result(cassandra.ask(Shunt(query)), timeout.duration)
			val actual2 = actual1.asInstanceOf[JObject].removeField(_._1 == "seeds")

			val expected = parse(s"""{
                   "query": "select * from $keyspace.test1 where col1 = 4;",
                   "success": true,
				   "data": []
                }""".stripMargin).asInstanceOf[JObject]

			assert(actual2 == expected)
		}

		"Not be triggered when query JSON field is not present" in {
			val queryString = s"select * from $keyspace.test1 where col1 = 'somevalue';"
			val query = parse( s"""{ "otherfield": "$queryString" } """).asInstanceOf[JObject]

			intercept[AskTimeoutException] {
				Await.result(cassandra.ask(Shunt(query)), Timeout(5.seconds).duration)
			}
		}
	}

	private def prepareDatabase() {
		scripts.foreach(script => {
			val json = parse(script).asInstanceOf[JObject]
			cassandra.underlyingActor.process(json)
		})
	}
}