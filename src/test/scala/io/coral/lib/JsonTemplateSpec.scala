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

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{Matchers, WordSpecLike}

class JsonTemplateSpec extends WordSpecLike with Matchers {
	"A JsonTemplate class" should {
		"instantiate from a template object" in {
			val templateJson = parse(
				"""{ "field1": "abc",
				  |  "field2": 123
				  |}""".stripMargin)
			val template = JsonTemplate(templateJson.asInstanceOf[JObject])
			template.interpret(parse("{}").asInstanceOf[JObject]) shouldBe templateJson
		}

		"substitute references (identified with a ${...}} construct" in {
			val templateJson = parse(
				"""{ "field1": "${abc}",
				  |  "field2": 123
				  |}""".stripMargin)
			val template = JsonTemplate(templateJson.asInstanceOf[JObject])
			val inputJson = parse(
				"""{ "def": 456,
				  |  "abc": 789
				  |}""".stripMargin)
			val outputJson = parse(
				"""{ "field1": 789,
				  |  "field2": 123
				  |}""".stripMargin)
			template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
		}

		"handle nested structure" in {
			val templateJson = parse(
				"""{ "a": "ALPHA",
				  |  "b": "${beta}",
				  |  "c": { "d": 123,
				  |         "e": { "ee": "${epsilon}" }
				  |       },
				  |  "f": 1,
				  |  "g": 1.0
				  |}""".stripMargin)
			val template = JsonTemplate(templateJson.asInstanceOf[JObject])
			val inputJson = parse(
				"""{ "beta": "xyz",
				  |  "epsilon": 987
				  |}""".stripMargin)
			val outputJson = parse(
				"""{ "a": "ALPHA",
				  |  "c": { "d": 123,
				  |         "e": { "ee": 987 }
				  |       },
				  |  "f": 1,
				  |  "b": "xyz",
				  |  "g": 1.0
				  |}""".stripMargin)
			template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
		}

		"handle expressions cf jsonExpressionParser" in {
			val templateJson = parse(
				"""{ "a": "${array[1]}",
				  |  "b": "${ field.sub.subsub }",
				  |  "c": 1.0
				  |}""".stripMargin)
			val template = JsonTemplate(templateJson.asInstanceOf[JObject])
			val inputJson = parse(
				"""{ "array": ["a0", "a1", "a2"],
				  |  "field": { "sub": { "subsub": 123, "bla": "bla" } },
				  |  "epsilon": 987
				  |}""".stripMargin)
			val outputJson = parse(
				"""{ "a": "a1",
				  |  "b": 123,
				  |  "c": 1.0
				  |}""".stripMargin)
			template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
		}

		"use null when values are not found" in {
			val templateJson = parse(
				"""{ "field1": "${abc}",
				  |  "field2": 123
				  |}""".stripMargin)
			val template = JsonTemplate(templateJson.asInstanceOf[JObject])
			val inputJson = parse(
				"""{ "def": 456
				  |}""".stripMargin)
			val outputJson = parse(
				"""{ "field1": null,
				  |  "field2": 123
				  |}""".stripMargin)
			template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
		}

	}

}
