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

import org.json4s.{JArray, JValue, JObject}
import org.json4s.JsonAST.JNothing
import scala.util.parsing.combinator.{PackratParsers, JavaTokenParsers}
import scala.util.parsing.input.CharSequenceReader

object JsonExpressionParser extends JavaTokenParsers with PackratParsers {
	abstract class FieldElement
	// Represents the complete list of identifiers ("field.array[0].reference['elem']")
	// A FieldReference is a concatenation of FieldElements.
	// A FieldElement is either a simple identifier, an array
	// access element or a dictionary access element.
	case class FieldReference(items: List[FieldElement])
	// Represents a simple identifier between dots
	case class JsonIdentifier(id: String) extends FieldElement
	// Represents an array access identifier ("field[0]")
	case class ArrayAccess(id: JsonIdentifier, index: Int) extends FieldElement
	// Represents a dictionary access identifier ("field['inner']")
	case class DictionaryAccess(id: JsonIdentifier, field: String) extends FieldElement
	object ReferenceAll extends FieldElement

	/**
	 * Parsers an expression string from a JSON field and
	 * returns the JSON value that the expression points to.
	 * @param expression The expression to parse.
	 * @return The JObject that was referred to in the expression
	 */
	def parse(expression: String, json: JObject): JValue = {
		val parseResult = phrase(local_field_reference)(
			new PackratReader(new CharSequenceReader(expression)))
		parseResult match {
			case Success(r, n) =>
				val value = r match {
					case r: FieldReference => getFieldValue(json, r)
				}

				value.asInstanceOf[JValue]
			case Failure(r, n) =>
				JNothing
			case _ =>
				JNothing
		}
	}

	/**
	 * Returns the value of a field that a FieldReference points to.
	 * @param json The JSON object to extract the value from
	 * @param id The FieldReference to extract
	 * @return The JValue that the FieldReference points to
	 */
	def getFieldValue(json: JObject, id: FieldReference): JValue = {
		// tempJson holds the result we want to return
		var tempJson: JValue = json

		id.items.foreach({
			case ReferenceAll => tempJson
			case i: JsonIdentifier =>
				tempJson = tempJson \ i.id
			case a: ArrayAccess =>
				val obj = tempJson \ a.id.id
				obj match {
					case array: JArray =>
						if (a.index < array.arr.length)
							tempJson = array(a.index)
						else return JNothing
					case _ => return JNothing
				}
			case d: DictionaryAccess =>
				tempJson = tempJson \ d.id.id \ d.field
			case _ =>
		})

		tempJson
	}

	type P[+T] = PackratParser[T]

	lazy val local_field_reference: P[FieldReference] =
		repsep(field_element, ".") ^^ { case i => FieldReference(i) }
	lazy val field_element: P[FieldElement] =
		reference_all | array_access | dictionary_access | json_identifier
	lazy val json_identifier: P[JsonIdentifier] =
		ident ^^ { case i => JsonIdentifier(i) }
	lazy val array_access: P[ArrayAccess] =
		json_identifier ~ "[" ~ wholeNumber ~ "]" ^^ {
			case id ~ "[" ~ index ~ "]" =>
				ArrayAccess(id, index.toInt)
		}
	lazy val dictionary_access: P[DictionaryAccess] =
		json_identifier ~ "[" ~ "'" ~ ident ~ "'" ~ "]" ^^ {
			case id ~ "[" ~ "'" ~ field ~ "'" ~ "]" =>
				DictionaryAccess(id, field)
		}
	lazy val reference_all: P[FieldElement] = "*" ^^ { case _ => ReferenceAll }
}