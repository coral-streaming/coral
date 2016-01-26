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

package io.coral.actors

import akka.actor.{Kill, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe, TestActorRef}
import akka.util.Timeout
import io.coral.actors.CoralActor.RegisterActor
import io.coral.actors.database.CassandraActor
import io.coral.actors.transform.StatsActor
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.junit.runner.RunWith
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.scalatest.junit.JUnitRunner
import scala.concurrent.duration._
import scala.concurrent.Await
import io.coral.actors.CollectDef._

@RunWith(classOf[JUnitRunner])
class CollectSpec(_system: ActorSystem)
	extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll
	with ScalaFutures {
	def this() = this(ActorSystem("coral"))
	implicit val timeout = Timeout(1 second)

	"A collect procedure of a Coral Actor" should {
		"Obtain state of other actors with collect" in {
			val stats = parse(s"""{
				| "name": "stats1",
				|  "type": "stats",
				|  "params": {
				|    "field": "field1"
				|  }
				|}""".stripMargin).asInstanceOf[JObject]
			val statsActor = TestActorRef[StatsActor](Props(new StatsActor(stats)), "stats1")

			val cassandra = parse(s"""{
				| "name": "cassandra1",
				|  "type": "cassandra",
				|  "params": {
				|    "seeds": ["192.168.100.101"],
				|    "keyspace": "coral"
				|  }
				|}""".stripMargin).asInstanceOf[JObject]
			val cassandraActor = TestActorRef[StatsActor](Props(new CassandraActor(cassandra)), "cassandra1")

			val collectJson = parse(s"""{
				| "name": "actor2",
				| "type": "zscore",
				| "params": {
				|   "collect": [
				|     { "alias": "average", "from": "stats1", "field": "avg" },
				|     { "alias": "nrobservations", "from": "stats1", "field": "count" },
				|     { "alias": "query", "from": "cassandra1", "data": { "query": "select * from system.hints;" }}
				|   ]
				|}}""".stripMargin).asInstanceOf[JObject]
			class CollectActor extends CoralActor(collectJson)
			val collectActor = TestActorRef[CollectActor](Props(new CollectActor()), "collect1")

			statsActor ! parse("""{ "field1": 68 }""").asInstanceOf[JObject]
			statsActor ! parse("""{ "field1": 16 }""").asInstanceOf[JObject]

			val probe = TestProbe()
			collectActor ! RegisterActor(probe.ref)

			val result1 = Await.result(collectActor
				.underlyingActor.collect[Int]("average"), timeout.duration)

			assert(result1 == 42)

			val result2 = Await.result(collectActor
				.underlyingActor.collect[Int]("nrobservations"), timeout.duration)

			assert(result2 == 2)

			val result3 = Await.result(collectActor
				.underlyingActor.collect[JObject]("query"), timeout.duration)

			// Not interested in the actual data, as long as the query is a success
			val success = (result3 \ "success").extract[Boolean]
			assert(success)
		}

		def statsTemplate(): JObject = {
			val string = s"""{
				   |  "name": "stats1",
				   |  "type": "stats",
				   |  "params": {
				   |    "field1": "statsfield1"
				   |  }
				   |}""".stripMargin
			parse(string).asInstanceOf[JObject]
		}

		def zscoreTemplate(collects: String): JObject = {
			val string = s"""{
				|  "name": "zscore1",
				|  "type": "zscore",
				|  "params": {
				|    "field1": "statsfield1"
				|  }, "collect": [$collects]
				|}""".stripMargin
			parse(string).asInstanceOf[JObject]
		}

		"Reject construction of an actor with an empty collect array that needs one" in {
			expectErrorsWithZscore("", List(noCollectSpecified))
		}

		"Reject construction of an actor without collect that needs one" in {
			val string = s"""{
				|  "name": "stats1",
				|  "type": "stats",
				|  "params": {
				|    "field1": "statsfield1"
				|  }
				|}""".stripMargin
			val json = parse(string).asInstanceOf[JObject]

			expectErrorsWithJSON(json, List(noCollectSpecified))
		}

		"Reject a collect definition without alias" in {
			expectErrorsWithZscore(s"""{ "from": "stats1", "field": "count" }""",
				List(collectsWithEmptyAlias))
		}

		"Reject a collect definition without from" in {
			expectErrorsWithZscore(s"""{ "alias": "count", "field": "count" }""",
				List(collectsWithEmptyFrom))
		}

		"Reject a collect definition without field" in {
			expectErrorsWithZscore(s"""{ "alias": "count", "from": "stats1" }""",
				List(collectsWithEmptyField))
		}

		"Reject a collect definition with double aliases" in {
			expectErrorsWithZscore(s"""
			   	| { "alias": "count", "from": "stats1", "field": "count" },
			   	| { "alias": "avg", "from": "stats1", "field": "avg" },
			   	| { "alias": "count", "from": "stats1", "field": "std" }""".stripMargin,
				List(duplicateAliases))
		}

		"Reject a collect definition of which the aliases do not match the aliases needed by the actor" in {
			expectErrorsWithZscore(s"""
			   	| { "alias": "wrongalias", "from": "stats1", "field": "count" },
			   	| { "alias": "avg", "from": "stats1", "field": "avg" },
			   	| { "alias": "otheralias", "from": "stats1", "field": "std" }""".stripMargin,
				List(aliasNotMatchingDefinition))
		}

		"Reject a collect definition with non-alphanumeric field values" in {
			expectErrorsWithZscore(s"""
			   	| { "alias": "count", "from": "stats1", "field": "%^&@" },
			   	| { "alias": "avg", "from": "stats1", "field": "###" },
			   	| { "alias": "std", "from": "stats1", "field": "*)(" }""".stripMargin,
				List(notAlphaNumeric))
		}

		"Reject a collect definition collecting state from itself" in {
			expectErrorsWithZscore(s"""
				| { "alias": "count", "from": "zscore1", "field": "count" },
				| { "alias": "avg", "from": "zscore1", "field": "avg" },
				| { "alias": "std", "from": "zscore1", "field": "std" }""".stripMargin,
				List(cannotCollectFromSelf))
		}

		"Reject a collect definition with empty alias" in {
			expectErrorsWithZscore(s"""
			   	| { "alias": "", "from": "stats1", "field": "count" },
			   	| { "alias": "avg", "from": "stats1", "field": "avg" },
			   	| { "alias": "std", "from": "stats1", "field": "std" }""".stripMargin,
				List(collectsWithEmptyAlias))
		}

		"Reject a collect definition with empty from" in {
			expectErrorsWithZscore(s"""
			   	| { "alias": "count", "from": "stats1", "field": "count" },
			   	| { "alias": "avg", "from": "", "field": "avg" },
			   	| { "alias": "std", "from": "stats1", "field": "std" }""".stripMargin,
				List(collectsWithEmptyFrom))
		}

		"Reject a collect definition with empty field" in {
			expectErrorsWithZscore(s"""
				| { "alias": "count", "from": "stats1", "field": "count" },
			    | { "alias": "avg", "from": "stats1", "field": "avg" },
			    | { "alias": "std", "from": "stats1", "field": "" }""".stripMargin,
				List(collectsWithEmptyField))
		}

		"Fail to obtain state of an actor that does not exist any more" in {
			val stats = parse(s"""{
				| "name": "stats2",
				|  "type": "stats",
				|  "params": {
				|    "field": "field1"
				|  }
				|}""".stripMargin).asInstanceOf[JObject]
			val statsActor = TestActorRef[StatsActor](Props(new StatsActor(stats)), "stats2")
			statsActor ! Kill

			val collectJson = parse(s"""{
				| "name": "actor2",
				| "type": "zscore",
				| "params": {
				|   "collect": [
				|     { "alias": "average", "from": "stats2", "field": "avg" },
				|     { "alias": "nrobservations", "from": "stats2", "field": "count" },
				|     { "alias": "std", "from": "stats2", "field": "std" }
				|   ]
				|}}""".stripMargin).asInstanceOf[JObject]
			class CollectActor extends CoralActor(collectJson)
			val collectActor = TestActorRef[CollectActor](Props(new CollectActor()), "collect2")

			statsActor ! parse("""{ "field1": 68 }""").asInstanceOf[JObject]
			statsActor ! parse("""{ "field1": 16 }""").asInstanceOf[JObject]

			val probe = TestProbe()
			collectActor ! RegisterActor(probe.ref)

			statsActor ! Kill
			expectNoMsg(20.millis)

			val failureMessage = "Collect actor failed to respond in time."

			val exception1 = intercept[Exception] {
				Await.result(collectActor
					.underlyingActor.collect[Int]("average"), timeout.duration)
			}

			assert(exception1.getMessage == failureMessage)

			val exception2 = intercept[Exception] {
				Await.result(collectActor
					.underlyingActor.collect[Int]("nrobservations"), timeout.duration)
			}

			assert(exception2.getMessage == failureMessage)
		}

		"Fail to obtain a nonexisting state field of an existing actor" in {
			val stats = parse(s"""{
				| "name": "stats3",
				|  "type": "stats",
				|  "params": {
				|    "field": "field1"
				|  }
				|}""".stripMargin).asInstanceOf[JObject]
			val statsActor = TestActorRef[StatsActor](Props(new StatsActor(stats)), "stats3")
			statsActor ! Kill

			val collectJson = parse(s"""{
				| "name": "actor2",
				| "type": "zscore",
				| "params": {
				|   "collect": [
				|     { "alias": "average", "from": "stats3", "field": "avg" },
				|     { "alias": "nrobservations", "from": "stats3", "field": "count" }
				|   ]
				|}}""".stripMargin).asInstanceOf[JObject]
			class CollectActor extends CoralActor(collectJson)
			val collectActor = TestActorRef[CollectActor](Props(new CollectActor()), "collect3")

			statsActor ! parse("""{ "field1": 68 }""").asInstanceOf[JObject]
			statsActor ! parse("""{ "field1": 16 }""").asInstanceOf[JObject]

			val probe = TestProbe()
			collectActor ! RegisterActor(probe.ref)

			val exception1 = intercept[Exception] {
				Await.result(collectActor
					.underlyingActor.collect[Int]("doesnotexist"), timeout.duration)
			}

			assert(exception1.getMessage == "Collect can not find field or JSON definition 'doesnotexist'.")
		}

		def expectErrorsWithZscore(collects: String, expected: List[String]) {
			expectErrorsWithJSON(zscoreTemplate(collects), expected)
		}

		def expectErrorsWithStats(expected: List[String]) {
			expectErrorsWithJSON(statsTemplate(), expected)
		}

		def expectErrorsWithJSON(json: JObject, expected: List[String]) {
			val result: Either[JObject, Boolean] = CollectDef.validCollectDef(json, expected)

			result match {
				case Left(answer) =>
					val actual = (answer \ "errors").extract[List[String]]
					assert(actual.sorted == expected.sorted)
				case Right(_) =>
					fail("Errors expected but no errors found.")
			}
		}
	}
}