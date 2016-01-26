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

package io.coral.utils

import akka.actor.Address
import io.coral.cluster.Machine
import org.scalatest._

class UtilsSpec
	extends WordSpecLike {
	"A Utils class" should {
		"Return the friendly time" in {
			val input = 1449848431868L
			val actual = Utils.friendlyTime(input)
			val expected = "2015-12-11T16:40:31.868"
			assert(actual == expected)
		}

		"Parse the friendly time" in {
			val actual = Utils.timeStampFromFriendlyTime("2015-12-11T16:40:31.868")
			val expected = Some(1449848431868L)
			assert(actual ==expected)
		}

		"Create string and convert back to timestamp" in {
			val time = Some(System.currentTimeMillis)
			val actual = Utils.timeStampFromFriendlyTime(Utils.friendlyTime(time.get))
			assert(actual == time)
		}

		"Create a machine from an actor path" in {
			val path1 = "akka.tcp://coral@127.0.0.1:2551/user/clusterMonitor"
			val actual1 = Utils.machineFromPath(path1)
			val expected1 = Machine(None, "127.0.0.1", 2551, List(), None)
			assert(actual1 == expected1)

			val path2 = "invalid.path"
			intercept[IllegalArgumentException] {
				Utils.machineFromPath(path2)
			}

			// No system name (always "coral") and no port
			val path3 = "akka.tcp://127.0.0.1/user/actor"
			intercept[IllegalArgumentException] {
				Utils.machineFromPath(path3)
			}

			// No port
			val path4 = "akka.tcp://coral@127.0.0.1/user/some/actor"
			intercept[IllegalArgumentException] {
				Utils.machineFromPath(path4)
			}

			val path5 = ""
			intercept[IllegalArgumentException] {
				Utils.machineFromPath(path5)
			}

			val path6 = null
			intercept[IllegalArgumentException] {
				Utils.machineFromPath(path6)
			}

			// No system name (always "coral")
			val path7 = "akka.tcp://127.0.0.1:2551/user/actor"
			intercept[IllegalArgumentException] {
				val actual7 = Utils.machineFromPath(path7)
			}
		}

		"Create an address from an actor path" in {
			val path1 = "akka.tcp://coral@127.0.0.1:2551/user/clusterMonitor"
			val actual1 = Utils.addressFromPath(path1)
			val expected1 = Address("akka.tcp", "coral", "127.0.0.1", 2551)
			assert(actual1 == expected1)

			val path2 = "akka.tcp://coral@127.0.0.1:2553"
			val actual2 = Utils.addressFromPath(path2)
			val expected2 = Address("akka.tcp", "coral", "127.0.0.1", 2553)
			assert(actual2 == expected2)

			val path3 = "akka.tcp://coral@127.0.0.1:2551/user/clusterMonitor"
			val actual3 = Utils.addressFromPath(path3)
			val expected3 = Address("akka.tcp", "coral", "127.0.0.1", 2551)
			assert(actual3 == expected3)

			val path4 = ""
			intercept[IllegalArgumentException] {
				Utils.addressFromPath(path4)
			}

			val path5 = null
			intercept[IllegalArgumentException] {
				Utils.addressFromPath(path5)
			}
		}
	}
}