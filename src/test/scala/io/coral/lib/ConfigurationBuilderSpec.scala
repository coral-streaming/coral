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

class ConfigurationBuilderSpec extends WordSpecLike with Matchers {
	"ConfigurationBuilder" should {
		"read application.conf" in {
			val builder = new ConfigurationBuilder("test.builder")
			val properties = builder.properties
			properties.getProperty("someProperty") shouldBe "someValue"
			properties.getProperty("anInteger") shouldBe "3"
		}

		"allow to add properties" in {
			val builder = new ConfigurationBuilder("test.builder")
			val properties = builder.properties
			properties.setProperty("vis", "blub")
			properties.getProperty("someProperty") shouldBe "someValue"
			properties.getProperty("vis") shouldBe "blub"
			properties.size shouldBe 3
		}

		"allow to replace properties" in {
			val builder = new ConfigurationBuilder("test.builder")
			val properties = builder.properties
			properties.setProperty("someProperty", "someOtherValue")
			properties.getProperty("someProperty") shouldBe "someOtherValue"
		}

		"properties (Java) return null for missing keys" in {
			val builder = new ConfigurationBuilder("test.builder")
			val properties = builder.properties
			properties.getProperty("aNonExistingProperty") shouldBe null
		}
	}
}