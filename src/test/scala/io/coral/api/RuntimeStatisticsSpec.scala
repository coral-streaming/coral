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

import io.coral.TestHelper
import org.junit.runner.RunWith
import org.scalatest.WordSpecLike
import org.scalatest.junit.JUnitRunner
import org.json4s._
import org.json4s.jackson.JsonMethods._

@RunWith(classOf[JUnitRunner])
class RuntimeStatisticsSpec
	extends WordSpecLike {
	"A RuntimeStatistics class" should {
		"Properly sum multiple statistics objects together" in {
			val counters1 = Map(
				(("actor1", "stat1") -> 100L),
				(("actor1", "stat2") -> 20L),
				(("actor1", "stat3") -> 15L))
			val counters2 = Map(
				(("actor2", "stat1") -> 20L),
				(("actor2", "stat2") -> 30L),
				(("actor2", "stat3") -> 40L))
			val counters3 = Map(
				(("actor2", "stat1") -> 20L),
				(("actor2", "stat2") -> 30L),
				(("actor2", "stat3") -> 40L),
				(("actor2", "stat4") -> 12L))
			val stats1 = RuntimeStatistics(1, 2, 3, counters1)
			val stats2 = RuntimeStatistics(2, 3, 4, counters2)
			val stats3 = RuntimeStatistics(4, 5, 6, counters3)

			val actual = RuntimeStatistics.merge(List(stats1, stats2, stats3))

			val expected = RuntimeStatistics(7, 10, 13,
				Map(("actor1", "stat1") -> 100,
					("actor1", "stat2") -> 20,
					("actor1", "stat3") -> 15,
					("actor2", "stat1") -> 20,
					("actor2", "stat2") -> 30,
					("actor2", "stat3") -> 40,
					("actor2", "stat4") -> 12))

			assert(actual == expected)
		}

		"Create a JSON object from a RuntimeStatistics object" in {
			val input = RuntimeStatistics(1, 2, 3,
				Map((("actor1", "stat1") -> 10L),
					(("actor1", "stat2") -> 20L)))

			val expected = parse(
				s"""{
				   |  "totalActors": 1,
				   |  "totalMessages": 2,
				   |  "totalExceptions": 3,
				   |  "counters": {
				   |    "total": {
				   |      "stat1": 10,
				   |      "stat2": 20
				   |    }, "actor1": {
				   |      "stat1": 10,
				   |      "stat2": 20
				   |    }
				   |  }
				   |}
				 """.stripMargin).asInstanceOf[JObject]

			val actual = RuntimeStatistics.toJson(input)
			assert(actual == expected)
		}

		"Create a RuntimeStatistics object from a JSON object" in {
			val input = parse(
				s"""{
				   |  "totalActors": 1,
				   |  "totalMessages": 2,
				   |  "totalExceptions": 3,
				   |  "counters": {
				   |    "total": {
				   |      "stat1": 10,
				   |      "stat2": 20
				   |    }, "actor1": {
				   |      "stat1": 10,
				   |      "stat2": 20
				   |    }
				   |  }
				   |}
				 """.stripMargin).asInstanceOf[JObject]

			val actual = RuntimeStatistics.fromJson(input)

			val expected = RuntimeStatistics(1, 2, 3,
				Map((("actor1", "stat1") -> 10L),
					(("actor1", "stat2") -> 20L)))

			assert(actual == expected)
		}
	}
}