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

object NameSuggester {
	/**
	 * Suggest a name based on a list of pre-existing names.
	 * If the name does not appear in the list, return the name itself.
	 * If it does appear in the list, add a number to it until it does
	 * not appear in the list any more, starting with 2.
	 * @param name The name to check.
	 * @param list The list to find the name in.
	 */
	def suggestName(name: String, list: List[String]): String = {
		if (!list.contains(name)) {
			name
		} else {
			val (first, last) = splitNumberAtEnd(name)
			val proposed = first + (last.toInt + 1).toString

			if (list.contains(proposed)) {
				suggestName(proposed, list)
			} else {
				proposed
			}
		}
	}

	/**
	 * Splits a string with a number at the end in the
	 * non-numeric and the numeric part.
	 * For example: "abcd123" => ("abcd", 123)
	 * @param name The actor name to split
	 * @return A tuple with a string and an int.
	 *         If no number is present a the end, the number
	 *         that is returned is 1.
	 */
	private def splitNumberAtEnd(name: String): (String, Int) = {
		val first = name.takeWhile(c => !c.isDigit)
		val last = {
			val numbers = name.reverse.takeWhile(c => c.isDigit).reverse
			if (numbers.length > 0) numbers.toInt else 1
		}

		(first, last)
	}
}
