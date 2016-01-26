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

import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterEach, BeforeAndAfterAll, WordSpecLike}
import org.scalatest.junit.JUnitRunner

/**
 * Class to test passing command line arguments
 * and passing .conf files to io.coral.api.Boot.
 * The order of settings processing is as follows:
 * 1) Command line arguments
 * 2) .conf file passed through "start --config <file>"
 * 3) Default configuration in jar in application.conf
 *
 * So command line arguments override everything else,
 * and the given config file overrides the default config file.
 */
@RunWith(classOf[JUnitRunner])
class BootConfigSpec
	extends WordSpecLike
	with BeforeAndAfterAll
	with BeforeAndAfterEach {
	"A Boot program actor" should {
		"Properly process given command line arguments for api and akka ports" in {
			val commandLine = CommandLineConfig(apiPort = Some(1234), akkaPort = Some(5345))
			val actual: CoralConfig = io.coral.api.Boot.getFinalConfig(commandLine)
			assert(actual.akka.remote.nettyTcpPort == 5345)
			assert(actual.coral.api.port == 1234)
		}

		"Properly process a given configuration file through the command line" in {
			val configPath = getClass().getResource("bootconfigspec.conf").getFile()
			val commandLine = CommandLineConfig(config = Some(configPath), apiPort = Some(4321))
			val actual: CoralConfig = io.coral.api.Boot.getFinalConfig(commandLine)
			// Overriden in bootconfigspec.conf
			assert(actual.akka.remote.nettyTcpPort == 6347)
			// Overridden in command line parameter
			assert(actual.coral.api.port == 4321)
			// Not overriden in command line or bootconfigspec.conf
			assert(actual.coral.cassandra.port == 9042)
		}
	}
}