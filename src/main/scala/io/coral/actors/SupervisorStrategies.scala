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

import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy.Resume
import akka.event.LoggingAdapter
import io.coral.utils.Utils

object SupervisorStrategies {
	/**
	 * A strategy in which an exception that is thrown by a child
	 * is logged by the parent after which execution is resumed.
	 * @param log The log to write the exception to.
	 */
	def logAndContinue(log: LoggingAdapter) = OneForOneStrategy() {
		case e: Exception => {
			log.error(s"Caught exception: ${e.getMessage}")
			log.error(Utils.stackTraceToString(e))
			log.info("Continue running due to supervisor strategy 'Resume'.")
			Resume
		}
	}
}