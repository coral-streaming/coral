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

package io.coral.lib

import scala.Double.NaN
import scala.math.sqrt

object SummaryStatistics {
	def mutable: SummaryStatistics = new MutableSummaryStatistics()

	private class MutableSummaryStatistics()
		extends SummaryStatistics {
		var count = 0L
		var average = NaN
		var variance = NaN
		var min = NaN
		var max = NaN

		def append(value: Double): Unit = count match {
			case 0L =>
				count = 1L
				average = value
				variance = 0.0
				min = value
				max = value
			case _ =>
				val newCount = count + 1L
				val weight = 1.0 / newCount
				val delta = weight * (value - average)
				variance += count * delta * delta - weight * variance
				average += delta
				count = newCount
				min = if (value < min) value else min
				max = if (value > max) value else max
		}

		def reset(): Unit = {
			count = 0L
			average = NaN
			variance = NaN
			min = NaN
			max = NaN
		}

	}

}

trait SummaryStatistics {
	def count: Long
	def average: Double
	def variance: Double
	def populationSd: Double = sqrt(variance)

	def sampleSd: Double =
		if (count > 1L) sqrt(variance * (count.toDouble / (count - 1.0)))
		else NaN

	def min: Double
	def max: Double
	def append(value: Double): Unit
	def reset(): Unit
}