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

import org.uncommons.maths.random.MersenneTwisterRNG

object Random extends Random(new MersenneTwisterRNG()) {
	def apply(source: java.util.Random) = new Random(source)
}

class Random(source: java.util.Random) {
	def uniform(): Stream[Double] = {
		Stream.continually(source.nextDouble())
	}

	def uniform(min: Double, max: Double): Stream[Double] = {
		val scale = max - min
		Stream.continually(min + scale * source.nextDouble())
	}

	def binomial(p: Double): Stream[Boolean] = {
		val uniformStream = uniform(0.0, 1.0)
		uniformStream.map(x => x < p)
	}
}