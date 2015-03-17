package io.coral.actors.database

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.actors.Messages.{GetField, Request}
import org.json4s.JsonAST.JValue
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

class TestCassandraActor(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

    implicit val timeout = Timeout(1.seconds)
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
            val actual = Await.result(cassandra.ask(Request(query)), timeout.duration)

            val expected = parse(
            """{
               "query": "select * from testkeyspace.test1",
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
            val actual = Await.result(cassandra.ask(Request(query)), timeout.duration)

            val expected = parse(
            """{
                "query": "select * from doesnotexist.doesnotexist",
                "result": false
            }"""
            )

            assert(actual == expected)
        }

        "Return false when selecting from a nonexisting table in a nonexisting keyspace" in {
            val query = parse("""{ "query": "select * from testkeyspace.doesnotexist" } """)
                .asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Request(query)), timeout.duration)

            val expected = parse(
                """{
                "query": "select * from testkeyspace.doesnotexist",
                "result": false
            }""")

            assert(actual == expected)
        }

        "Return false when executing an illegal query" in {
            val query = parse("""{ "query": "this is not a query" } """).asInstanceOf[JObject]
            val actual = Await.result(cassandra.ask(Request(query)), timeout.duration)

            val expected = parse(
                """{
                "query": "this is not a query",
                "result": false
            }""")

            assert(actual == expected)
        }

        "Insert data and check that it is inserted" in {
            val query1 = parse(
                """{ "query": "insert into testkeyspace.test1
                  |(col1, col2, col3) values ('test1', 100, 100);" } """.stripMargin)
                .asInstanceOf[JObject]

            Await.result(cassandra.ask(Request(query1)), timeout.duration)

            val queryString = "select count(*) from testkeyspace.test1 where col1 = 'test1';"
            val complete = s"""{ "query": "$queryString" } """
            val query2 = parse(complete).asInstanceOf[JObject]
            val actual2 = Await.result(cassandra.ask(Request(query2)), timeout.duration)

            val expected2 = parse(
                s"""{ "query": "$queryString",
                   |"columns": [{ "count": "bigint" }], "data": [[ 1 ]] }""".stripMargin)

            assert(actual2 == expected2)
        }
    }

    def prepareDatabase() {
        scripts.foreach(script => {
            val json = parse(script).asInstanceOf[JObject]
            cassandra ! Request(json)
            expectNoMsg(50.millis)
        })
    }

    def createCassandraActor(): ActorRef = {
        val json = parse("""{ "seeds": ["127.0.0.1"], "keyspace": "system" }""")
            .asInstanceOf[JObject]
        system.actorOf(Props(new CassandraActor(json)), "cassandra")
    }
}