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

import org.scalatest.{Matchers, WordSpecLike}

class RandomSpec extends WordSpecLike with Matchers {
	"The Random object" should {
		"provide a stream of uniformly distributed doubles" in {
			val source = NotSoRandom(0.3, 0.8)
			val random = new Random(source)
			val stream = random.uniform()
			stream.head should be(0.3) // as example of simple use case
			stream.take(2).toList should be(List(0.3, 0.8))
		}

		"provide a stream of uniformly distributed doubles with scale" in {
			val source = NotSoRandom(0.3, 0.8)
			val random = new Random(source)
			val stream = random.uniform(2.0, 6.0)
			stream.take(2).toList should be(List(3.2, 5.2))
		}

		"provide a stream of weighted true/false values (binomial values)" in {
			val source = NotSoRandom(0.3, 0.8, 0.299999, 0.0, 1.0)
			val random = new Random(source)
			val stream = random.binomial(0.3)
			stream.take(5).toList should be(List(false, false, true, true, false))
		}
	}
}