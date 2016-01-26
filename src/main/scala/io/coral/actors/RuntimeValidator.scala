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

import akka.event.slf4j.Logger
import io.coral.actors.CoralActorFactory._
import io.coral.api.security.{AuthenticatorChooser}
import io.coral.utils.Utils
import scaldi.Injector
import scala.collection.mutable.ListBuffer
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

object RuntimeValidator {
	implicit val formats = org.json4s.DefaultFormats

	val noActorSection = "There is no valid actors section present. Please add an actors section."
	val noActors = "There are no actors present in the actors section. " +
		"Please add actors to the actors section."
	val actorsWithoutNames = "There are actors without names. " +
		"Please make sure that each actor has a name tag and that it is not empty."
	val noRuntimeName = "The configuration does not have a runtime name specified. " +
		"Please specify a name field."
	val noOwner = "The configuration does not have an owner. " +
		"Please specify an owner field."
	val duplicateActorNames = "There are actors with duplicate names. " +
		"Please make sure each actor has an unique name."
	val invalidActorDefinitions = "There are invalid actor definitions. " +
		"Please check each actor definition against the specification of that actor."
	val noLinksSection = "There is no valid links section present. Please add a links section."
	val noLinksPresent = "There are no links present in the links section. " +
		"Please add links to the links section."
	val linksWithoutFromOrTo = "There are links without a from and to attribute. " +
		"Please make sure each link has both a from and a to field."
	val linksWithoutActorDef = "Not all actors mentioned in the links are " +
		"actual actor names in the configuration. " +
		"Please make sure that each actor name referenced in the links " +
		"section is defined in the actor section."
	val circularLinks = "There are circular links present. " +
		"Please make sure that there are no cycles present in the links section. For instance:" +
		"a1 -> a2, a2 -> a3, a3 -> a1."
	val orphanedActors = "There are actors which are not being referenced in any " +
		"from or to field of the links section. Please make sure that each actor is connected at least once."
	val uuidWithLDAP = "Cannot use an owner UUID when creating a new runtime in combination with LDAP authentication."

	/**
	 * Checks the JSON definition of a runtime to see
	 * whether it is correct.
	 * This does not check whether the suggested name for the runtime
	 * is unique for the given user.
	 * @param json The JSON object containing the runtime definition.
	 * @param authInfo The AuthInfo object to check whether the combination
	 *                 of parameters given is valid.
	 * @return True when the definition is correct, false otherwise.
	 *         Nothing is actually instantiated in this method, only checked.
	 */
	def validRuntimeDefinition(json: JObject)(
		implicit injector: Injector): Either[JObject, Boolean] = {
		var errors = ListBuffer.empty[String]

		def filledArray(array: JArray): Boolean = {
			array.children.size > 0
		}

		def allActorsHaveNames(array: JArray): Boolean = {
			array.children.forall(actor => {
				val name = (actor \ "name").extractOpt[String]
				name.isDefined &&
					name.get.nonEmpty
			})
		}

		def allActorDefinitionsValid(array: JArray): Boolean = {
			array.children.forall(json => {
				// Run it through the actor factory to see if it is valid
				val validProps = getProps(json).isDefined

				if (!validProps) {
					Logger(getClass.getName).error(s"Invalid actor definition: ${compact(render(json))}")
				}

				validProps
			})
		}

		def noDuplicateNames(array: JArray): Boolean = {
			val names = array.children.map(x => {
				(x \ "name").extractOrElse("")
			})

			if (names == Nil) {
				true
			} else {
				names.distinct.size == names.size
			}
		}

		def linkNamesMatchActorNames(actors: JArray, links: JArray): Boolean = {
			val froms = links.arr.map(l => (l \ "from").extractOrElse(""))
			val tos = links.arr.map(l => (l \ "to").extractOrElse(""))
			val actorNames = actors.arr.map(a => (a \ "name").extractOrElse(""))

			froms.forall(actorNames.contains) && tos.forall(actorNames.contains)
		}

		def noActorsWithoutLinks(actors: JArray, links: JArray): Boolean = {
			val froms = links.arr.map(l => (l \ "from").extractOrElse(""))
			val tos = links.arr.map(l => (l \ "to").extractOrElse(""))
			val actorNames = actors.arr.map(a => (a \ "name").extractOrElse(""))

			actorNames.exists(froms.contains) || actorNames.exists(tos.contains)
		}

		def linkFromToDefined(links: JArray): Boolean = {
			links.children.forall(link => {
				(link \ "from").extractOpt[String].isDefined &&
				(link \ "to").extractOpt[String].isDefined
			})
		}

		def notUUIDWithLDAP(): Boolean = {
			val owner = (json \ "owner").extractOpt[String]

			AuthenticatorChooser.config match {
				case Some(c) if c.coral.authentication.mode == "ldap"
					&& owner.isDefined
					&& Utils.tryUUID(owner).isDefined =>
					errors += uuidWithLDAP
					false
				case _ =>
					true
			}
		}

		val name = (json \ "name").extractOrElse("")
		val owner = (json \ "owner").extractOrElse("")
		val hasName = !name.isEmpty
		val hasOwner = !owner.isEmpty

		if (!hasName) errors += noRuntimeName

		val actors = (json \ "actors").extractOpt[JArray]
		val validActors = actors match {
			case None =>
				errors += noActorSection
				false
			case Some(array) =>
				val a1 = filledArray(array)
				val a2 = allActorsHaveNames(array)
				val a3 = noDuplicateNames(array)
				val a4 = allActorDefinitionsValid(array)

				if (!a1) errors += noActors
				if (!a2) errors += actorsWithoutNames

				// Only say that there are actors with duplicate names if they all have names
				if (a2) {
					if (!a3) errors += duplicateActorNames
				}

				if (!a4) errors += invalidActorDefinitions

				a1 && a2 && a3 && a4
		}

		val links = (json \ "links").extractOpt[JArray]
		val validLinks = links match {
			case None =>
				errors += noLinksSection
				false
			case Some(array) =>
				val l1 = filledArray(array)
				val l2 = linkFromToDefined(array)
				val l3 = linkNamesMatchActorNames(actors.get, array)
				val l4 = noActorsWithoutLinks(actors.get, array)

				if (!l1) errors += noLinksPresent
				if (!l2) errors += linksWithoutFromOrTo
				if (!l3) errors += linksWithoutActorDef
				if (!l4) errors += orphanedActors

				l1 && l2 && l3 && l4
		}

		if (hasName && validActors && validLinks && notUUIDWithLDAP()) {
			Right(true)
		} else {
			Left(("success" -> false) ~ ("errors" -> JArray(errors.map(JString(_)).toList)))
		}
	}
}
