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

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.Predef
import scala.collection.immutable.Map

object RuntimeStatistics {
	implicit val formats = org.json4s.DefaultFormats

	/**
	 * Create a new RuntimeStatistics object from a JSON object.
	 * @param json The JSON object to use.
	 * @return A RuntimeStatistics object.
	 */
	def fromJson(json: JObject): RuntimeStatistics = {
		val totalActors = (json \ "totalActors").extract[Int]
		val totalMessages = (json \ "totalMessages").extract[Int]
		val totalExceptions = (json \ "totalExceptions").extract[Int]

		val counters = (json \ "counters").extract[JObject].removeField(_._1 == "total")
		val items: List[(String, JValue)] = counters.asInstanceOf[JObject].obj

		val statistics: Map[(String, String), Long] = items.flatMap(i => {
			val actorName = i._1
			val stats = i._2

			stats.asInstanceOf[JObject].obj.map(j => {
				val statName = j._1
				val statValue = j._2.extract[Long]

				((actorName, statName), statValue)
			})
		}).toMap

		RuntimeStatistics(totalActors,
			totalMessages, totalExceptions, statistics)
	}

	/**
	 * Convert the RuntimeStatistics object to a JSON representation.
	 * @param stats The RuntimeStatistics object to convert.
	 */
	def toJson(stats: RuntimeStatistics): JObject = {
		val perStat = stats.counters.groupBy(_._1._2)

		val totals: Map[String, Long] = perStat.map(x => {
			val statName = x._1
			val sum = x._2.map(_._2).sum
			(statName -> sum)
		}).toList.sortBy(_._1).toMap

		val counters: JObject = stats.counters.toMap.toList.foldLeft(JObject())((acc, curr) => {
			val perActor = stats.counters.groupBy(_._1._1)

			val result: Map[String, JObject] = perActor.map(x => {
				val actorName = x._1
				val map = x._2

				val thisActor = (actorName -> map.toMap.toList.foldLeft(JObject())((acc, curr) => {
					val statName = curr._1._2
					val statValue = curr._2

					acc ~ (statName -> JInt(statValue))
				}))

				thisActor
			})

			result
		})

		("totalActors" -> stats.totalActors) ~
		("totalMessages" -> stats.totalMessages) ~
		("totalExceptions" -> stats.totalExceptions) ~
		("counters" -> (counters ~ ("total" -> totals)))
	}

	/**
	 * Merge multiple RuntimeStatistics objects together into a
	 * single RuntimeStatistics object. This means summing up
	 * the total number of actors, messages and exceptions, and
	 * combining the counters into a single map.
	 * @param list The list of RuntimeStatistics objects to combine.
	 * @return A single RuntimeStatistics object with all the
	 *         counters combined.
	 */
	def merge(list: List[RuntimeStatistics]): RuntimeStatistics = {
		RuntimeStatistics(
			list.map(_.totalActors).sum,
			list.map(_.totalMessages).sum,
			list.map(_.totalExceptions).sum,
			list.flatMap(_.counters).toMap)
	}

	/**
	 * Creates an empty RuntimeStatistics object.
	 */
	def empty() = RuntimeStatistics(0, 0, 0, Map.empty[(String, String), Long])
}

/**
 * Represents the statistics of a runtime. These can be summed ("merged") by the
 * method above. RuntimeStatistics can be collected on the level of a platform,
 * RuntimeAdminActors (single nodes) or on the level of a single runtime.
 * The higher levels can aggregate the lower level runtime statistics with the
 * merge method.
 * @param totalActors The total number of actors found on that level.
 * @param totalMessages The total number of processed messages on that level.
 *                      The CoralActor performs a stats("json") call to increase this number.
 * @param totalExceptions The total number of exceptions thrown on that level.
 *                        The CoralActor performs a stats("failure") call to
 *                        increase this number.
 * @param counters The counters of individual objects. These contain ((String, String), Long)
 *                 tuples where the first string is the name of the actor and the second
 *                 string is the name of the statistic.
 */
case class RuntimeStatistics(// The total number of actors in this runtime
							 totalActors: Int,
							 // The total number of messages processed in all actors
							 totalMessages: Long,
							 // The total number of exceptions raised in all actors
							 totalExceptions: Long,
							 // (actor name, stat name) -> stat
							 counters: Map[(String, String), Long])