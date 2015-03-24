package io.coral.actors.database

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.actors.Messages.{Trigger, GetField, Shunt}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class IntegrationTestCassandraActor(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

    implicit val timeout = Timeout(100.seconds)
    val duration = timeout.duration

    def this() = this(ActorSystem("testSystem"))
    val cassandra = createCassandraActor()

    val scripts = Array(
        """{ "query": "drop keyspace if exists testkeyspace;" } """,
        """{ "query": "create keyspace testkeyspace with replication = { 'class': 'SimpleStrategy',
          |'replication_factor': 1 };" } """.stripMargin,
        """{ "query": "use keyspace testkeyspace;" } """,
        """{ "query": "drop table if exists test1; drop table if exists test2;" } """,
        """{ "query": "create table testkeyspace.test1 (
                     col1 text,
                     col2 int,
                     col3 float,
                     primary key (col1));" } """,
        """{ "query": "create table testkeyspace.test2 (
                     data1 int,
                     data2 text,
                     data3 float,
                     primary key (data2));" } """,
        """{ "query": "insert into testkeyspace.test1 (col1, col2, col3) values ('blabla', 10, 34.1984);" } """,
        """{ "query": "insert into testkeyspace.test1 (col1, col2, col3) values ('abc', 42, 57.661);" } """,
        """{ "query": "insert into testkeyspace.test1 (col1, col2, col3) values ('stuff', 48, 25.63);" } """,
        """{ "query": "insert into testkeyspace.test2 (data1, data2, data3) values (53, 'foo', 18.35);" } """,
        """{ "query": "insert into testkeyspace.test2 (data1, data2, data3) values (72, 'bar', 96.625);" } """
    )

    prepareDatabase()

    "A Cassandra actor" should {
        "Return the correct state in JSON" in {
            val actual = Await.result(cassandra.ask(GetField("schema")), timeout.duration)

            val expected = parse(
            """{
               "connected": true,
               "keyspace": "testkeyspace",
               "schema": [{
                  "test2": [{
                     "data2": "text"
                  }, {
                     "data1": "int"
                  }, {
                     "data3": "float"
                  }]
               }, {
                  "test1": [{
                     "col1": "text"
                  }, {
                     "col2": "int"
                  }, {
                     "col3": "float"
                  }]
               }]
            }""")

            assert(actual == expected)
        }

        "Return a JSON representation of a ResultSet" in {
            val query = parse("""{ "query": "select * from testkeyspace.test1" } """)
                .asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)

            val expected = parse(
            """{
               "query": "select * from testkeyspace.test1",
               "success": true,
               "columns": [{
                  "col1": "varchar"
               },{
                  "col2": "int"
               },{
                  "col3": "float"
               }], "data": [
                  [ "abc", 42, 57.6609992980957 ],
                  [ "stuff", 48, 25.6299991607666 ],
                  [ "blabla", 10, 34.19839859008789 ]
               ]
            }""")

            assert(actual == expected)
        }

        "Return false when selecting from nonexisting table and keyspace" in {
            val query = parse("""{ "query": "select * from doesnotexist.doesnotexist" } """)
                .asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)

            val expected = parse(
            """{
                "query": "select * from doesnotexist.doesnotexist",
                "success": false,
                "error": "Keyspace doesnotexist does not exist"
            }"""
            )

            assert(actual == expected)
        }

        "Return false when selecting from a nonexisting table in a nonexisting keyspace" in {
            val query = parse("""{ "query": "select * from testkeyspace.doesnotexist" } """)
                .asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)

            val expected = parse(
                """{
                "query": "select * from testkeyspace.doesnotexist",
                "success": false,
                "error": "unconfigured columnfamily doesnotexist"
            }""")

            assert(actual == expected)
        }

        "Return false when executing an illegal query" in {
            val query = parse("""{ "query": "this is not a query" } """).asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)

            val expected = parse(
                """{
                "query": "this is not a query",
                "success" false,
                "error": "line 1:0 no viable alternative at input 'this' ([this]...)"
            }""")

            assert(actual == expected)
        }

        "Return data in the correct format" in {
            val insert = parse(
                """{ "query": "insert into testkeyspace.test1
                  |(col1, col2, col3) values ('test1', 100, 100);" } """.stripMargin)
                .asInstanceOf[JObject]

            Await.result(cassandra.ask(Shunt(insert)), timeout.duration)

            val queryString = "select count(*) from testkeyspace.test1 where col1 = 'test1';"
            val complete = s"""{ "query": "$queryString" } """
            val select = parse(complete).asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Shunt(select)), timeout.duration)

            val expected = parse(
                s"""{ "query": "$queryString","success": true, "columns": [{ "count": "bigint" }], "data": [[ 1 ]] }""".stripMargin)

            assert(actual == expected)
        }

        "Execute a valid delete query that returns no results" in {
            val queryString = "delete from testkeyspace.test1 where col1 = 'doesnotexist';"
            val query = parse(s"""{ "query": "$queryString" } """).asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)
            val expected = parse(s"""{ "query": "$queryString", "success": true }""".stripMargin)
            assert(actual == expected)
        }

        "Return an empty list on select without result" in {
            val queryString = "select * from testkeyspace.test1 where col1 = 'doesnotexist';"
            val query = parse(s"""{ "query": "$queryString" } """).asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Shunt(query)), timeout.duration)
            val expected = parse(
                s"""{
                   "query": "select * from testkeyspace.test1 where col1 = 'doesnotexist';",
                   "success": true,
                   "columns": [{
                      "col1":"varchar"
                   },{
                      "col2": "int"
                   },{
                      "col3": "float"
                   }], "data": []
                }""".stripMargin).asInstanceOf[JObject]
            assert(actual == expected)
        }

        "Not be triggered when query JSON field is not present" in {
            val queryString = "select * from testkeyspace.test1 where col1 = 'somevalue';"
            val query = parse(s"""{ "otherfield": "$queryString" } """).asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Shunt(query)), Timeout(2.seconds).duration)
            println(actual)
        }
    }

    def prepareDatabase() {
        scripts.foreach(script => {
            val json = parse(script).asInstanceOf[JObject]
            cassandra ! Trigger(json)
            expectNoMsg(50.millis)
        })
    }

    def createCassandraActor(): ActorRef = {
        val json = parse("""{ "seeds": ["127.0.0.1"], "keyspace": "system" }""")
            .asInstanceOf[JObject]
        system.actorOf(Props(new CassandraActor(json)), "cassandra")
    }
}