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

import io.coral.actors.transform.ZscoreActor
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.JObject
import scala.collection.mutable.{Map => mMap, ListBuffer}

/**
 * Class that represents a collect definition.
 * A collect definition is a JSON array stating the external data
 * that a Coral actor needs to fulfill a trigger request.
 * These fields are specified in the constructor of the actor.
 */
object CollectDef {
	implicit val formats = org.json4s.DefaultFormats

	val cannotCollectFromSelf = "There are actors that try to collect state from themselves. " +
		"Please make sure that no actors collect state from themselves."
	val noCollectSpecified = "There are actors with missing collect definitions. Please make sure that all " +
		"actors that expect a collect definition have defined one."
	val collectsWithEmptyAlias = "There are collect definitions with an empty or missing 'alias' value. " +
		"Please make sure that each collect definition has a filled in 'alias' value."
	val collectsWithEmptyFrom = "There are collect definitions with an empty or missing 'from' value. " +
		"Please make sure that each collect definition has a filled in 'from' value."
	val collectsWithEmptyField = "There are collect definitions with an empty or missing 'field' value. " +
		"Please make sure that each collect definition has a filled in 'field' value."
	val duplicateAliases = "There are duplicate alias names. " +
		"Please make sure that each alias name is unique."
	val aliasNotMatchingDefinition = "There are alias names that do not match the definition of " +
		"the actor to which the collect definition belongs. Please make sure that you check the " +
		"documentation for the required names for each collect field."
	val notAlphaNumeric = "There are field, alias or from values which do not consist solely of " +
		"alphanumeric characters. Please make sure each of these values is a " +
		"string value with only alphanumeric characters."
	val fromNonExistingActor = "A collect is specified that collects from an actor that is " +
		"not present in the runtime definition. Please make sure that each collect 'from' field refers to an" +
		"actor name that actually exists in the runtime definition."

	/**
	 * Checks whether the supplied actor constructor is valid.
	 * If the actor collects information from other actors, the list of
	 * collect aliases is used to check if they are all present.
	 * @param json The JSON constructor of the actor
	 * @param expectedAliases The expected aliases of that actor.
	 *                        Each alias must occur exactly once in the constructor.
	 * @return Either a Left(JObject) with errors or a Right(true) if it is
	 *         a valid collect definition for that actor.
	 */
	def validCollectDef(json: JObject, expectedAliases: List[String]): Either[JObject, Boolean] = {
		def noCollect(json: JObject): Boolean = {
			val collect = (json \ "collect").extractOpt[JArray]
			val noneAtAll = !collect.isDefined
			val empty = if (noneAtAll) true else collect.get.children.size == 0
			noneAtAll || empty
		}

		def doubleAliases(json: JObject): Boolean = {
			val list = getAll(json, "alias")
			list.distinct.size != list.size
		}

		def aliasesDoNotMatch(json: JObject): Boolean = {
			val aliases: List[String] = getAll(json, "alias")
			val actorType = (json \ "type").extractOpt[String]

			if (!actorType.isDefined) {
				false
			} else {
				val expectedAliases = actorType.get match {
					case "zscore" => ZscoreActor.collectAliases
					case _ => List()
					// Add other actors with collect aliases here
				}

				aliases.sorted != expectedAliases.sorted
			}
		}

		def emptyAliases(json: JObject): Boolean = {
			empty(json, "alias")
		}

		def emptyFroms(json: JObject): Boolean = {
			empty(json, "from")
		}

		def emptyFields(json: JObject): Boolean = {
			empty(json, "field")
		}

		def getAll(json: JObject, value: String): List[String] = {
			val array = (json \ "collect").extractOpt[JArray]

			array match {
				case None => List()
				case Some(a) =>
					a.asInstanceOf[JArray].children.map(c => {
						val x = (c \ value).extractOpt[String]
						if (x.isDefined) x.get else null
					}).filter(_ != null)
			}
		}

		def defined(json: JObject, value: String): Boolean = {
			forAnyCollect(c => {
				val result = (c \ value).extractOpt[String].isDefined
				result
			}, false)
		}

		def empty(json: JObject, value: String): Boolean = {
			forAnyCollect(c => {
				val v = (c \ value).extractOpt[String]
				if (v.isDefined) {
					if (v.get.isEmpty) true else false
				} else true
			}, false)
		}

		def forAnyCollect(f: JValue => Boolean, default: Boolean): Boolean = {
			val array = (json \ "collect").extractOpt[JArray]

			array match {
				case None => default
				case Some(a) => a.children.exists(f)
			}
		}

		def notOnlyAlphaNumeric(json: JObject): Boolean = {
			val alphaNumeric = "^[a-zA-Z0-9_]*$"

			!getAll(json, "alias").forall(_.matches(alphaNumeric)) ||
			!getAll(json, "from").forall(_.matches(alphaNumeric)) ||
			!getAll(json, "field").forall(_.matches(alphaNumeric))
		}

		def referringToSelf(json: JObject): Boolean = {
			val selfName = (json \ "name").extract[String]
			getAll(json, "from").contains(selfName)
		}

		var errors = ListBuffer.empty[String]

		if (expectedAliases.length == 0) {
			throw new Exception("Cannot validate collect definition with empty alias list.")
		}

		if (noCollect(json)) {
			// If this happens, don't bother with checking the rest
			errors += noCollectSpecified
		} else {
			val emptyAlias = emptyAliases(json)
			val emptyFrom = emptyFroms(json)
			val emptyField = emptyFields(json)
			val duplicateAlias = doubleAliases(json)

			if (emptyAlias) errors += collectsWithEmptyAlias
			if (emptyFrom) errors += collectsWithEmptyFrom
			if (emptyField) errors += collectsWithEmptyField
			if (duplicateAlias) errors += duplicateAliases
			if (notOnlyAlphaNumeric(json)) errors += notAlphaNumeric
			if (referringToSelf(json)) errors += cannotCollectFromSelf

			// Only complain about not matching definition if other errors are fixed
			if (!emptyAlias && !emptyFrom && !emptyField && !duplicateAlias) {
				if (aliasesDoNotMatch(json)) errors += aliasNotMatchingDefinition
			}
		}

		if (errors.isEmpty) {
			Right(true)
		} else {
			Left(("success" -> false) ~ ("errors" -> JArray(errors.map(JString(_)).toList)))
		}
	}

	/**
	 * Parse a CollectDef from a JSON constructor.
	 * @param json The JSON constructor of the CoralActor to parse the
	 *             CollectDef from.
	 * @return Some(def) if parsing succeeded, None otherwise.
	 */
	def get(json: JObject): Option[CollectDef] = {
		val collects = (json \ "params" \ "collect").extractOpt[JArray]
		val result = new CollectDef()

		collects match {
			case Some(array) =>
				array.arr.foreach(c => {
					val alias = (c \ "alias").extractOpt[String]
					val from = (c \ "from").extractOpt[String]
					val field = (c \ "field").extractOpt[String]
					val data = (c \ "data").extractOpt[JObject]

					if (alias.isDefined && from.isDefined) {
						if (field.isDefined) {
							// It is a static field query
							result.putFieldCollectDef(alias.get, (from.get, field.get))
						} else if (data.isDefined) {
							// It is a JSON request
							result.putJsonCollectDef(alias.get, (from.get, data.get))
						}
					} else {
						// If just one field is missing, fail
						return None
					}
				})
			case None => None
		}

		Some(result)
	}
}

/**
 * Represents the "collect" definition of a CoralActor.
 * The collect definition states all external data dependencies
 * that a CoralActor has on other actors.
 *
 * There are two types of collect definitions:
 * - fields: Collect a static field from the state of another actor
 * - jsons: Give another actor a JSON object, let it process
 *   the JSON object and return with the answer
 */
class CollectDef {
	/**
	 * Collect definitions that ask for a state
	 * field of a certain CoralActor.
	 */
	// <alias, <actor name, field>>
	val fields = mMap.empty[String, (String, String)]

	/**
	 * Collect definitions that ask for a response
	 * on a JSON input object.
	 */
	// <alias, <actor name, json>>
	val jsons = mMap.empty[String, (String, JObject)]

	/**
	 * Add a new JSON collect definition to this actor's collect definition.
	 * @param alias The alias under which the JSON collect definition is known.
	 * @param actorAndJson The name of the actor and the JSON object to
	 *                     pass to the actor.
	 */
	def putJsonCollectDef(alias: String, actorAndJson: (String, JObject)) = {
		jsons.put(alias, actorAndJson)
	}

	/**
	 * Put a new collect definition into the definition map.
	 * @param alias The alias of the collect definition.
	 *              This is used by the CoralActor to fetch the definition.
	 * @param actorAndField The actor name and the field name that belongs
	 *                      to this collect definition.
	 */
	def putFieldCollectDef(alias: String, actorAndField: (String, String)) = {
		fields.put(alias, actorAndField)
	}

	/**
	 * Get a collect definition from the definition map
	 * @param alias The alias of the collect.
	 * @return A pair of (actor name, field) that enables
	 *         fetching the field from the actor with that name.
	 */
	def getFieldCollectDef(alias: String): Option[(String, String)] = {
		fields.get(alias)
	}

	/**
	 * Gets a JSON collect definition from a given alias.
	 * @param alias The alias of the collect definition.
	 * @return A pair (actor name, JSON) that belongs to that
	 *         collect definition.
	 */
	def getJsonCollectDef(alias: String): Option[(String, JObject)] = {
		jsons.get(alias)
	}
}