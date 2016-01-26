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

import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConverters._

/**
 * Helper class to obtain properties, but have application defined properties already added
 * E.g. Kafka uses java properties for producer and consumer instantiation
 * @param path configuration path (e.g. "kafka.consumer")
 */
case class ConfigurationBuilder(path: String) {
	private lazy val config: Config = ConfigFactory.load().getConfig(path)

	def properties: Properties = {
		val props = new Properties()
		val it = config.entrySet().asScala

		it.foreach(entry => {
			props.setProperty(entry.getKey, entry.getValue.unwrapped.toString)
		})

		props
	}
}
