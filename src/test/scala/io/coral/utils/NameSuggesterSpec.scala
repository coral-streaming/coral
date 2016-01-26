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

import org.scalatest._

class NameSuggesterSpec
	extends WordSpecLike
	with BeforeAndAfterAll
	with BeforeAndAfterEach {
	"A NameSuggester" should {
		"Suggest the same name when no items present in the list" in {
			val others = List()
			val actual = NameSuggester.suggestName("name1", others)
			val expected = "name1"
			assert(actual == expected)
		}

		"Suggest another name when the same name is present in the list" in {
			val others = List("name1")
			val actual = NameSuggester.suggestName("name1", others)
			val expected = "name2"
			assert(actual == expected)
		}

		"Suggest another name when the suggested name is also present in the list" in {
			val others = List("name1", "name2", "name3")
			val actual = NameSuggester.suggestName("name1", others)
			val expected = "name4"
			assert(actual == expected)
		}

		"Suggest a new name for an input without number at the end" in {
			val others = List("name", "othername")
			val actual = NameSuggester.suggestName("name", others)
			val expected = "name2"
			assert(actual == expected)
		}

		"Suggest a new name for an input with only numbers" in {
			val others = List("1234", "othername")
			val actual = NameSuggester.suggestName("1234", others)
			val expected = "1235"
			assert(actual == expected)
		}

		"Suggest a new name even with doubles in the given list" in {
			val others = List("name1", "name1")
			val actual = NameSuggester.suggestName("name1", others)
			val expected = "name2"
			assert(actual == expected)
		}
	}
}