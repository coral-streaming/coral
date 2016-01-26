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
import org.json4s.native.JsonMethods._
import org.scalatest.{Matchers, WordSpecLike}

class JsonExpressionParserSpec extends WordSpecLike with Matchers {
	implicit val formats = org.json4s.DefaultFormats

	"A JsonExpressionParser" should {
		"Properly extract array values" in {
			val expr = "field[0]"
			val json = parse(s"""{
				| "field": [
              	|	{ "value": "first" },
                | 	{ "value": "second" },
             	| 	{ "value": "third" }
                | ]}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = parse("""{ "value": "first" }""").asInstanceOf[JObject]
			assert(actual == expected)
		}

		"Properly extract value from simple field" in {
			val expr = "field"
			val json = parse(s"""{
				| "field": [
				|	{ "value": "first" },
				| 	{ "value": "second" },
				| 	{ "value": "third" }
				| ]}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = parse(s"""[
				|   { "value": "first" },
           		|   { "value": "second" },
           		|   { "value": "third" }
        		| ]""".stripMargin).asInstanceOf[JArray]
			assert(actual == expected)
		}

		"Properly extract double from simple field" in {
			val expr = "field"
			val json = parse( """{ "field": 2.0 }""")
				.asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = 2.0
			assert(actual == JDouble(expected))
		}

		"Properly extract value from nested field" in {
			val expr = "field.value3.nested"
			val json = parse(s"""{
				| "field": [
		  		| { "value1": "first" },
                | { "value2": "second" },
             	| {
			 	|   "value3": {
                |  	  "nested": 1,
                |     "double": 2.0,
                |	  "array": [1, 2, 3]
                |   }
                |  }
                ]
              }""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JInt(1)
			assert(actual == expected)
		}

		"Properly extract fields after an array" in {
			val expr = "field.inner.array[1].inner2"
			val json = parse(s"""{
				| "field": [
				| { "value": "first" },
				| { "value": "second" },
             	| {
			 	|   "inner": {
                |     "nested": 1,
                |     "double": 2.0,
                |     "array": [
                |       { "inner1": "bla" },
                |       { "inner2": "value" }
              	|     ]
                |   }
				|  }
                |]}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JString("value")
			assert(actual == expected)
		}

		"Properly extract dictionary names in simple fields" in {
			val expr = "field['inner']"
			val json = parse(s"""{
			 	| "field":
             	| {
			 	|   "inner": {
                |     "nested": 1,
                |     "double": 2.0,
                |     "array": [
                |       { "inner1": "bla" },
                |       { "inner2": "value" }
              	|    ]
                |  }
				|}}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)

			val expected = parse(s"""{
            	| "nested": 1,
            	| "double": 2.0,
            	| "array": [
              	|   { "inner1": "bla" },
              	|   { "inner2": "value" }
            	| ]
          		|}""".stripMargin).asInstanceOf[JObject]
			assert(actual == expected)
		}

		"Properly extract dictionary names after an array" in {
			val expr = "array[2].inner2['nested']"
			val json = parse(s"""{
				| "array": [
              	|   { "value": "first" },
				|   { "inner": { "field": 1 }},
             	|   { "inner2": {
                |     "nested": 1,
                |     "double": 2.0,
                |     "array": [
                |       { "inner1": "bla" },
                |       { "inner2": "value" }
             	|     ]
               	|   }
                |  }
                |]}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JInt(1)
			assert(actual == expected)
		}

		"Properly extract dictionary names after a nested field" in {
			val expr = "inner.object['field']"
			val json = parse(
				"""{ "inner": { "object": { "field": 2.0 }}}""")
				.asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JDouble(2.0)
			assert(actual == expected)
		}

		"Do not extract nonexisting simple field" in {
			val expr = "doesnotexist"
			val json = parse("""{ "field": 2.0 }""")
				.asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Do not extract nonexisting nested field" in {
			val expr = "does.not.exist"
			val json = parse(s"""{
			 	| "field": [
              	|   { "value": "first" },
                |   { "value": "second" },
             	|   { "value": {
                |     "nested": 1,
                |     "double": 2.0,
                |      "array": [1, 2, 3]
               	|    }
                |  }
                |]}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Do not extract array values outside of array bounds" in {
			val expr = "field[4]"
			val json = parse(s"""{
			 	| "field": [
              	|   { "value": "first" },
                |   { "value": "second" },
             	|   { "value": {
                | "nested": 1,
                | "double": 2.0,
                | "array": [1, 2, 3]
               	| }
                |}]}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Do not extract an array access on an element that is not an array" in {
			val expr = "inner[0]"
			val json = parse("""{ "inner": { "object": { "field": 2.0 }}}""")
				.asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Do not extract a dictionary access on an array" in {
			val expr = "array['field']"
			val json = parse(s"""{
			 	| "array": [
              	|   { "value": "first" },
                |   { "inner": { "field": 1 }},
             	|   { "inner2": {
                |     "nested": 1,
                | 	  "double": 2.0,
                |     "array": [
                |       { "inner1": "bla" },
                |       { "inner2": "value" }
              	|     ]
                |   }
                |  }
                ]}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Do not extract nonexisting inner field in expression string" in {
			val expr = "inner.nonexisting.field"
			val json = parse("""{ "inner": { "object": { "field": 2.0 }}}""")
				.asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Do not extract incorrectly formatted expression string" in {
			val expr = "inner...object]]field']"
			val json = parse("""{ "inner": { "object": { "field": 2.0 }}}""")
				.asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Do not extract another incorrectly formatted expression string" in {
			val expr = "..[][inner..field['blabla']"
			val json = parse("""{ "inner": { "object": { "field": 2.0 }}}""")
				.asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Do not extract yet another incorrectly formatted expression string" in {
			val expr = "inner[0][1].field"
			val json = parse("""{ "inner": { "object": { "field": 2.0 }}}""")
				.asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = JNothing
			assert(actual == expected)
		}

		"Extract the whole json for reference *" in {
			val expr = "*"
			val json = parse(s"""{
				| "field": {
            	|   "inner": {
                |	"nested": 1,
                | 	"double": 2.0,
                | 	"array": [
                |     { "inner1": "bla" },
                |     { "inner2": "value" }
                |   ]
             	|  }
          		|}}""".stripMargin).asInstanceOf[JObject]
			val actual = JsonExpressionParser.parse(expr, json)
			val expected = json
			assert(actual == expected)
		}
	}
}