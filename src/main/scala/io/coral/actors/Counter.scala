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

import scala.collection.mutable.{Map => mMap}

/**
 * A class that keeps track of counters of type long.
 */
class Counter {
	private val counters = mMap.empty[String, Long]

	/**
	 * Increment the given counter. If it does not exist yet,
	 * it is initialized with a value of 1. If it does exit,
	 * increment its value by 1.
	 * @param name The name of the counter to increment.
	 */
	def increment(name: String) {
		counters.get(name) match {
			case None =>
				counters.put(name, 1)
			case Some(value) =>
				counters.update(name, value.asInstanceOf[Long] + 1)
		}
	}

	/**
	 * Store a counter with a given value in the map.
	 * @param name The name of the counter to store
	 * @param value The value to store with the name.
	 */
	def once(name: String, value: Long) {
		counters.put(name, value)
	}

	/**
	 * Get a value from the map. If it is not found, return 0
	 * @param name The name of the value to retrieve.
	 * @return The value, 0 if not found.
	 */
	def get(name: String): Long = {
		counters.getOrElse(name, 0)
	}

	def toMap = counters.toMap
}