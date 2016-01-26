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

package io.coral.cluster

import io.coral.actors.Counter
import scala.collection.immutable.Map

/**
 * Represents platform statistics that can be obtained through /api/stats
 */
case class PlatformStatistics(// The total number of runtimes on the platform
							  totalRuntimes: Int,
							  // The total number of actors in the system
							  totalActors: Int,
							  // The start time of the platform
							  runningSince: Long,
							  // The total number of messages processed
							  totalMessages: Long,
							  // The total number of exceptions thrown in all runtimes
							  nrExceptions: Long,
							  // A list of all machines of the platform.
							  members: List[Machine],
							  // ... other monitoring metrics here
							  // Any runtime-specific monitoring metrics in this map
							  children: Map[String, Counter])