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

object NotSoRandom {
	def apply(xs: Double*): java.util.Random = new NotSoRandomDouble(xs: _*)

}

// Note: we do not test the underlying source of random numbers here
class NotSoRandomDouble(xs: Double*) extends java.util.Random {
	var i = -1

	override def nextDouble(): Double = {
		i += 1
		if (i < xs.length) xs(i)
		else Double.NaN // don't throw an exception as stream may compute one ahead
	}
}
