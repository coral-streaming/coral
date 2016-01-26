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

import akka.actor.{PoisonPill, Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.actors.RuntimeAdminActor._
import io.coral.api.DefaultModule
import org.json4s.JObject
import org.junit.runner.RunWith
import org.scalacheck.Prop.Exception
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import org.json4s.native.JsonMethods._
import org.json4s._
import org.json4s.JsonDSL._
import akka.pattern.ask

@RunWith(classOf[JUnitRunner])
class RuntimeValidatorSpec
	extends WordSpecLike
	with BeforeAndAfterAll
	with Matchers {
	implicit val timeout = Timeout(2.seconds)
	implicit val formats = org.json4s.DefaultFormats
	implicit val system = ActorSystem()
	implicit val injector = new DefaultModule(system.settings.config)

	"An RuntimeValidator" must {
		"Accept a valid runtime definition" in {
			expectNoErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "name": "generator1",
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "generator1", "to": "log1" }]
			}""".stripMargin)
		}

		"Reject a runtime with multiple errors" in {
			expectErrors(s"""{
				| "owner": "ab12cd",
				| "actors": [{
				| "name": "generator1",
				| "type": "generator",
				| "params": {
				| 	"format": {
				|		"field1": "N(100, 10)"
				|	}, "timer": {
				|		"rate": 10,
				|		"times": 10,
				|		"delay": 0
				|	}
				| }}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				| }}], "links": [
				| 	{ "from": "actor1", "to": "actor2" }
				| ]
			}""".stripMargin, List(
				RuntimeValidator.noRuntimeName,
				RuntimeValidator.linksWithoutActorDef,
				RuntimeValidator.orphanedActors))
		}

		"Reject a runtime with empty links section" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				| 	"name": "generator1",
				|  "type": "generator",
				|  "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": []
			}""".stripMargin, List(
				RuntimeValidator.noLinksPresent,
				RuntimeValidator.orphanedActors))
		}

		"Reject a runtime with invalid links section" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				| 	"name": "generator1",
				|  "type": "generator",
				|  "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": {
				|      "invalid": true
				| }
			}""".stripMargin, List(RuntimeValidator.noLinksSection))
		}

		"Reject a runtime with invalid actors" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				| 	"name": "generator1",
				|  "type": "generator",
				|  "params": {
				|		"wrongfield1": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"wrongfield2": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [{
				| 	"from": "generator1", "to": "log1"
				| }]
			}""".stripMargin, List(RuntimeValidator.invalidActorDefinitions))
		}

		"Reject a runtime without links" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				| 	"name": "generator1",
				|  "type": "generator",
				|  "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}
				| }]
			}""".stripMargin, List(RuntimeValidator.noLinksSection))
		}

		"Reject a runtime without actors" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [],
				| "links": [{
				|    "from": "doesnotexist1", "to": "doesnotexist2"
				| }]
			}""".stripMargin, List(
				RuntimeValidator.noActors,
				RuntimeValidator.linksWithoutActorDef,
				RuntimeValidator.orphanedActors))
		}

		"Reject a runtime with empty actor names" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "name": "",
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "generator1", "to": "log1" }]
			}""".stripMargin, List(
				RuntimeValidator.actorsWithoutNames,
				RuntimeValidator.linksWithoutActorDef,
				RuntimeValidator.orphanedActors))
		}

		"Reject a runtime with missing actor names" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "generator1", "to": "log1" }]
			}""".stripMargin, List(
				RuntimeValidator.actorsWithoutNames,
				RuntimeValidator.linksWithoutActorDef,
				RuntimeValidator.orphanedActors))
		}

		"Reject a runtime with links that do not match actor names" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "name": "generator1",
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "doesnotexist1", "to": "doesnotexist2" }]
			}""".stripMargin, List(
				RuntimeValidator.linksWithoutActorDef,
				RuntimeValidator.orphanedActors))

			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "name": "generator1",
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "generator1", "to": "doesnotexist1" }]
			}""".stripMargin, List(RuntimeValidator.linksWithoutActorDef))

			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "name": "generator1",
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "doesnotexist1", "to": "log1" }]
			}""".stripMargin, List(RuntimeValidator.linksWithoutActorDef))
		}

		"Reject a runtime with duplicate actor names" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "name": "name1",
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "name1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "name1", "to": "log1" }]
			}""".stripMargin, List(RuntimeValidator.duplicateActorNames))
		}

		"Reject a runtime with orphaned actors" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "name": "generator1",
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}, {
				|	"name": "log2",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "name1", "to": "log1" }]
			}""".stripMargin, List(RuntimeValidator.linksWithoutActorDef))
		}

		"Reject a runtime with links that do not have from and to defined" in {
			expectErrors(s"""{
				| "name": "runtime1",
				| "owner": "ab12cd",
				| "actors": [{
				|   "name": "generator1",
				|   "type": "generator",
				|   "params": {
				|		"format": {
				|			"field1": "N(100, 10)"
				|		}, "timer": {
				|			"rate": 10,
				|			"times": 10,
				|			"delay": 0
				|		}
				|	}}, {
				|	"name": "log1",
				|	"type": "log",
				|	"params": {
				|		"file": "/tmp/coral.log"
				|	}}], "links": [
				| 	{ "from": "generator1" }]
			}""".stripMargin, List(
				RuntimeValidator.linksWithoutFromOrTo,
				RuntimeValidator.linksWithoutActorDef))
		}

		def expectNoErrors(string: String) {
			val json = parse(string)
			val actual: Either[JObject, Boolean] = RuntimeValidator.validRuntimeDefinition(
				json.asInstanceOf[JObject])
			val expected = Right(true)

			assert(actual == expected)
		}

		def expectErrors(string: String, errors: List[String]) {
			val json = parse(string)
			val actual: Either[JObject, Boolean] = RuntimeValidator.validRuntimeDefinition(
				json.asInstanceOf[JObject])
			val errorString = errors.mkString("\"", "\", \"", "\"")
			val expected = Left(parse( s"""{ "success": false, "errors": [ $errorString ] }""".stripMargin))

			assert(actual == expected)
		}
	}
}