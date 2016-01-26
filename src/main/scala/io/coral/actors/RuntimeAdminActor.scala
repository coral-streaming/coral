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

import java.util.UUID
import akka.actor._
import akka.util.Timeout
import akka.pattern.ask
import io.coral.api.security.Authenticator.{GetAllRuntimes, GetUserUUIDFromUniqueName, Invalidate, CheckNameAndUser}
import io.coral.api.security.{AuthInfo, AcceptAllUser, User}
import io.coral.api.{RuntimeStatistics, Project, CoralConfig, Runtime}
import io.coral.cluster.ClusterDistributor
import io.coral.utils.{Utils, NameSuggester}
import scaldi.Injector
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import org.json4s._
import org.json4s.JsonDSL._
import akka.pattern.pipe

object RuntimeAdminActor {
	case class CreateRuntime(json: JObject, authInfo: AuthInfo)
	case class GetRuntimeInfo(runtime: Runtime)
	case class GetRuntimeActor(name: String)
	case class GetAllRuntimeDefinitions()
	case class DeleteRuntime(runtime: Runtime)
	case class DeleteAllRuntimes()
	case class StartRuntime(runtime: Runtime)
	case class StopAllRuntimes()
	case class StopRuntime(runtime: Runtime)
	case class GetActors(runtime: Runtime)
	case class GetLinks(runtime: Runtime)
	case class GetActorPath(name: String, actorName: String)
	case class UpdateRuntimeSettings(runtime: Runtime, json: JObject)
	case class GetAllRuntimeStatistics()
	case class GetRuntimeStatistics(runtime: Runtime)
	case class GetActorStatistics(actorPath: ActorPath, runtime: Runtime)
	case class GetFromDefinition(runtime: Runtime, value: String)
}

/**
 * This actor manages all runtimes, creates new runtimes
 * and deletes runtimes, starts runtimes and stops runtimes on a single machine.
 * Therefore, it is one of the most important actors in the entire Coral system.
 * One of such actors runs on each node that has started io.coral.api.Boot.
 */
class RuntimeAdminActor(implicit injector: Injector, ec: ExecutionContext, config: CoralConfig)
	extends Actor with ActorLogging {
	import RuntimeAdminActor._
	implicit val timeout = Timeout(10.seconds)
	implicit val formats = org.json4s.DefaultFormats
	var clusterDistributor: ActorRef = _
	val clusterMonitor = context.actorSelection("/user/root/clusterMonitor")
	val authenticator = context.actorSelection("/user/root/authenticator")

	// The start time of the node on which the RuntimeAdminActor runs
	var runningSince: Long = _

	override def preStart() {
		runningSince = System.currentTimeMillis
		clusterDistributor = context.actorOf(Props(new ClusterDistributor()), "clusterDistributor")
	}

	override def supervisorStrategy = SupervisorStrategies.logAndContinue(log)

	override def receive = {
		case RuntimeAdminActor.CreateRuntime(json: JObject, authInfo: AuthInfo) =>
			createRuntime(json, authInfo)
		case ClusterDistributor.CreateRuntimeLocally(uniqueName, jsonDef, owner) =>
			createRuntimeLocally(uniqueName, jsonDef, owner)
		case GetRuntimeInfo(runtime: Runtime) =>
			getRuntimeInfo(runtime)
		case GetFromDefinition(runtime, value) =>
			getFromDefinition(runtime, value)
		case GetActors(runtime: Runtime) =>
			getFromDefinition(runtime, "actors")
		case GetLinks(runtime: Runtime) =>
			getFromDefinition(runtime, "links")
		case GetActorPath(name: String, actorName: String) =>
			getActorPath(name, actorName)
		case GetRuntimeActor(name: String) =>
			sender ! context.child(name)
		case GetAllRuntimeDefinitions() =>
			getAllRuntimeDefinitions()
		case DeleteRuntime(runtime: Runtime) =>
			deleteRuntime(runtime)
		case DeleteAllRuntimes() =>
			deleteAllRuntimes()
		case UpdateRuntimeSettings(runtime: Runtime, json: JObject) =>
			updateRuntimeSettings(runtime, json)
		case RuntimeAdminActor.StartRuntime(runtime: Runtime) =>
			startRuntime(runtime)
		case RuntimeAdminActor.StopRuntime(runtime: Runtime) =>
			stopRuntime(runtime)
		case GetAllRuntimeStatistics() =>
			getAllRuntimeStatistics()
		case GetRuntimeStatistics(runtime: Runtime) =>
			getRuntimeStatistics(runtime, Some(sender()))
		case GetActorStatistics(actorPath, runtime) =>
			getActorStatistics(actorPath, runtime)
	}

	/**
	 * Get the actor path of an actor with the given name running in the
	 * runtime with the given name.
	 * @param name The name of the runtime the actor is in
	 * @param actorName The name of the actor
	 * @return The actor path of the specific actor.
	 */
	def getActorPath(name: String, actorName: String): Future[Option[ActorPath]] = {
		context.child(name) match {
			case None =>
				Future.successful(None)
			case Some(c) =>
				c.ask(RuntimeActor.GetActorPath(actorName))
					.asInstanceOf[Future[Option[ActorPath]]]
		}
	}

	def getActorStatistics(actorPath: ActorPath, runtime: Runtime): JObject = {
		null
	}

	/**
	 * Create a new runtime.
	 * @param json The json definition for the runtime to create.
	 * @return The id of the runtime if succesfully created.
	 */
	def createRuntime(json: JObject, authInfo: AuthInfo)(implicit injector: Injector) {
		val originalSender = sender()
		log.info("Received request to create new runtime")
		val valid = RuntimeValidator.validRuntimeDefinition(json)

		valid match {
			case Right(_) =>
				try {
					val name = (json \ "name").extract[String]
					val ownerString = (json \ "owner").extractOpt[String] match {
						// No owner specified, assume currently logged in user as owner
						case None => authInfo.uniqueName
						case Some(o) => o
					}

					makeSureUnique(ownerString, name, json).flatMap(answer => {
						val uniqueName = answer._1
						val newJson = answer._2
						val owner = answer._3

						// Ask the cluster distributor to find a node to run it on
						clusterDistributor.ask(ClusterDistributor
							.CreateRuntime(uniqueName, newJson, owner)) pipeTo originalSender
					})
				} catch {
					case e: Exception =>
						log.error(e.getMessage)
						log.error(Utils.stackTraceToString(e))
						originalSender !
							("action" -> "Create new runtime") ~
							("success" -> false) ~
							("reason" -> e.getMessage)
				}
			case Left(reasons: JObject) =>
				log.error("Invalid runtime definition found.")
				originalSender ! reasons
		}
	}

	/**
	 * Create a runtime on the machine where this ClusterDistributor is running.
	 * This is still not actually the method that creates the runtime, that is done
	 * by the RuntimeActor, which responds to the CreateRuntime message.
	 * @param uniqueName The unique name of the runtime to create
	 * @param jsonDef The json definition of the runtime to create
	 * @param owner The owner of the runtime
	 */
	def createRuntimeLocally(uniqueName: String, jsonDef: JObject, owner: UUID) {
		val originalSender = sender()
		log.info(
			"Creating runtime locally with ClusterDistributor " +
			   s"""with address "${Utils.getFullSelfPath(self)}"""".stripMargin)
		val runtimeActor = context.actorOf(Props(new RuntimeActor(uniqueName, owner)), uniqueName)
		runtimeActor.ask(RuntimeActor.CreateRuntime(jsonDef, uniqueName)) pipeTo originalSender
	}

	/**
	 * Helper function for the makeSureUnique function below.
	 * First converts the owner String into an UUID by looking up the owner.
	 * @param owner The owner UUID to parse
	 * @param name The name of the runtime
	 * @param json The json object of the runtime
	 * @return (uniqueName, json, owner UUID).
	 *         The uniqueName is changed if the original proposal already exists.
	 *         The json is also updated to reflect the new name if necessary.
	 */
	def makeSureUnique(owner: String, name: String, json: JObject): Future[(String, JObject, UUID)] = {
		val ownerUUID = Utils.tryUUID(owner)

		ownerUUID match {
			case None =>
				val question = authenticator.ask(GetUserUUIDFromUniqueName(owner))

				question.flatMap(answer => {
					val uuid = answer.asInstanceOf[Option[UUID]]

					if (!uuid.isDefined) {
						throw new Exception(s"""Owner with name $owner not found.""")
					}

					makeSureUnique(uuid.get, name, json)
				})
			case Some(uuid) =>
				makeSureUnique(uuid, name, json)
		}
	}

	/**
	 * Make sure that the name given in the JSON is unique (for a given owner)
	 * and while you're at it, also check if the owner exists.
	 */
	def makeSureUnique(owner: UUID, name: String, json: JObject): Future[(String, JObject, UUID)] = {
		authenticator.ask(CheckNameAndUser(name, owner)).map(answer => {
			val data = answer.asInstanceOf[(Boolean, List[String], Option[Either[User, AcceptAllUser]])]

			val wasEverDeleted: Boolean = data._1
			val otherRuntimesOnDisk: List[String] = data._2
			val ownerUser: Option[Either[User, AcceptAllUser]] = data._3

			val uniqueOwnerName = ownerUser match {
				case None =>
					val message = s"""Owner with ID ${owner.toString} not found."""
					log.error(message)
					throw new Exception(message)
				case Some(Left(user)) => user.uniqueName
				case Some(Right(acceptAll)) => acceptAll.name
			}

			// A child may have been created which was not yet persisted to disk. Check this as well.
			val otherRuntimesInMemory: List[String] = context.children
				.map(_.path.name)
				// Is also a child of this actor, not interesting here
				.filter(_ != "clusterDistributor")
				// Only look at runtimes of the current user
				// Runtime name consists of "<unique user name>-<runtime name>"
				.filter(_.split("-")(0) == uniqueOwnerName).toList

			val otherRuntimes = (otherRuntimesInMemory ++ otherRuntimesOnDisk
				// We already know that the runtimes on disk belong to the current user
				.map(_.split("-")(1)).toList).distinct

			val newName = NameSuggester.suggestName(name, otherRuntimes)
			val uniqueName = uniqueOwnerName + "-" + newName

			if (wasEverDeleted) {
				log.warning(
					s"""A runtime with the name "${owner + "-" + name}" was previously created and then deleted. """ +
						s"""To prevent replaying of old events, the alternative name "$uniqueName" will be used.""")
			} else if (newName != name) {
				log.warning(
					s"""A runtime with the same name already exists. """ +
						s"""The alternative name "$uniqueName" will be used.""")
			}

			// Replace the name in the JSON definition as well
			val newJson = (json transformField {
				case JField("name", JString(oldName)) =>
					if (oldName == name) {
						JField("name", JString(newName))
					} else {
						// It matches other "name" fields as well, so ignore these
						JField("name", JString(oldName))
					}
			}).asInstanceOf[JObject]

			val withOwner: JObject = ((json \ "owner").extractOpt[String] match {
				case Some(_) =>
					newJson.transformField {
						// If the user entered a unique user name, replace it with the owner UUID
						case JField("owner", JString(oldOwner)) =>
							JField("owner", JString(owner.toString))
					}
				case None =>
					// No owner given, fill it in now
					val idx = newJson.obj.indexWhere(_._1 == "name") + 1
					val (first, last) = newJson.obj.splitAt(idx)
					val result = ListBuffer.empty[(String, JValue)]

					result ++= first
					result ++= List(("owner" -> owner.toString))
					result ++= last

					new JObject(result.toList)
			}).asInstanceOf[JObject]

			(uniqueName, withOwner, owner)
		})
	}

	/**
	 * Return the project definition associated with a given project name.
	 * @param projectName The name of the project
	 * @return The project definition associated with that project name.
	 */
	def getProject(projectName: String): Project = {
		Project()
	}

	/**
	 * Deletes all runtimes on the platform.
	 * @return A JSON overview object showing the result
	 *         of the delete action.
	 */
	def deleteAllRuntimes() {
		val originalSender = sender()
		log.warning("Deleting all runtimes")
		val future = forAllRuntimes("Delete runtimes", deleteRuntime)
		future pipeTo originalSender

		future onComplete {
			case Success(_) => authenticator ! Invalidate()
			case _ =>
		}
	}

	/**
	 * Stop all runtimes on the platform.
	 * @return A JSON overview object showing the result
	 *         of the stop action.
	 */
	def stopAllRuntimes() {
		val originalSender = sender()
		log.warning("Stopping all runtimes")
		val future = forAllRuntimes("Stop runtimes", stopRuntime)
		future pipeTo originalSender

		future onComplete {
			case Success(_) => authenticator ! Invalidate()
			case _ =>
		}
	}

	/**
	 * Returns a list of all runtime definitions.
	 * @return A JArray with all runtime definitions.
	 */
	def getAllRuntimeDefinitions()(implicit ec: ExecutionContext) {
		val originalSender = sender()
		log.info("Getting definitions of all runtimes")

		authenticator.ask(GetAllRuntimes()).mapTo[List[Runtime]].flatMap(allRuntimes => {
			Future.sequence(allRuntimes.map(runtime => getRuntimeInfo(runtime)))
				.map(list => {
					val answer: JObject = ("runtimes" -> JArray(list))
					answer
				})
		}) pipeTo originalSender
	}

	/**
	 * Obtain runtime statistics for all runtimes and combine
	 * them into a single JSON object.
	 * @return A list of RuntimeStatistics objects of all runtimes
	 */
	def getAllRuntimeStatistics() {
		val originalSender = sender()
		log.info("Getting runtime statistics of all runtimes")

		// Future[List[Runtime]] => Future[List[JObject]] => Future[List[RuntimeStatistics]]

		val runtimes = authenticator.ask(GetAllRuntimes()).asInstanceOf[Future[List[Runtime]]]

		val stats: Future[List[JObject]] = runtimes.flatMap((allRuntimes: List[Runtime]) => {
			val allStats = allRuntimes.map(runtime => getRuntimeStatistics(runtime, None))
			Future.sequence(allStats)
		})

		val answer: Future[List[RuntimeStatistics]] = stats.map((list: List[JObject]) => {
			list.map(RuntimeStatistics.fromJson(_))
		})

		answer pipeTo originalSender
	}

	/**
	 * Obtain the runtime statistics for the given runtime.
	 * If the RuntimeAdminActor cannot handle the request himself because he is not
	 * on the machine on which the runtime is started, then redirect the request to the
	 * RuntimeAdminActor on the machine on which the runtime runs.
	 * @param runtime The runtime to get the statistics for.
	 */
	def getRuntimeStatistics(runtime: Runtime, receiver: Option[ActorRef]): Future[JObject] = {
		forwardOrHandle(runtime, GetRuntimeStatistics(runtime), receiver, (runtime: Runtime) => {
			context.child(runtime.uniqueName) match {
				case None =>
					val message =
						s"""Cannot get runtime statistics for "${runtime.uniqueName}"""" +
						  "because the runtime does not exist."
					log.error(message)
					Future.failed(throw new Exception(message))
				case Some(child) =>
					val future = child.ask(RuntimeActor.GetRuntimeStatistics())
					if (receiver.isDefined) future pipeTo receiver.get
					future.asInstanceOf[Future[JObject]]
			}
		})
	}

	/**
	 * Forwards a message to the RuntimeAdminActor on that node or handles the request itself
	 * if this actor is the registered admin actor (according to the path in runtime.adminPath).
	 * @param runtime The runtime on which a request needs to be performed
	 * @param message The message to handle
	 * @param returnTo The actor to which the result must be sent to
	 * @param handler The handler in case this actor is the registered admin actor of the runtime.
	 * @return A future JSON object containing the result of the operation.
	 */
	def forwardOrHandle(runtime: Runtime, message: Any, returnTo: Option[ActorRef],
						handler: (Runtime) => Future[JObject]): Future[JObject] = {
		if (!config.coral.cluster.enabled) {
			// No choice but to run it locally
			val future = handler(runtime)
			if (returnTo.isDefined) future pipeTo returnTo.get
			future
		} else {
			val completePath = akka.serialization.Serialization.serializedActorPath(self)
			val selfPath = completePath.substring(0, completePath.lastIndexOf("#"))

			if (selfPath != runtime.adminPath) {
				val admin = context.actorSelection(runtime.adminPath)
				val answer = admin.ask(message)
				if (returnTo.isDefined) answer pipeTo returnTo.get
				answer.asInstanceOf[Future[JObject]]
			} else {
				val future = handler(runtime)
				if (returnTo.isDefined) future pipeTo returnTo.get
				future
			}
		}
	}

	/**
	 * Performs an action on all runtimes on the system.
	 * The action takes the name of the runtime and maps
	 * it to a future JObject result.
	 * @param description A description of the action to perform, used in the JSON response.
	 * @param action The action to perform on each runtime.
	 * @return The result of performing the action on all runtimes.
	 */
	def forAllRuntimes(description: String, action: (Runtime) => Future[JObject]): Future[JObject] = {
		// Do this for all runtimes in this actor, and send
		// the same thing to all RuntimeAdminActors on other node
		// Simply get a list of all runtimes, the forwardOrHandle method does the rest
		authenticator.ask(GetAllRuntimes()).mapTo[List[Runtime]].flatMap(allRuntimes => {
			val rawResult: Future[List[JObject]] = Future.sequence(allRuntimes
				.filter(_.status != 4).map(runtime => action(runtime)))

			rawResult.map((list: List[JObject]) => {
				list.arr.size match {
					case 0 =>
						("action" -> description) ~
						("success" -> true)
					case n =>
						// It is a success if all actions are a success
						val success = list.arr.forall((r: JValue) => {
							(r \ "success").extract[Boolean] == true
						})

						val individualSuccess = list.arr.map((r: JValue) => {
							val name = (r \ "name").extract[String]
							val result = (r \ "success").extract[Boolean]
							JField(name, result)
						})

						("action" -> description) ~
						("success" -> success) ~
						("results" -> individualSuccess)
				}
			})
		})
	}

	/**
	 * Delete a runtime with a given name.
	 * Kill it if it is running.
	 * @param runtime The runtime to delete.
	 */
	def deleteRuntime(runtime: Runtime): Future[JObject] = {
		val originalSender = sender()

		forwardOrHandle(runtime, DeleteRuntime(runtime), Some(originalSender),
			(runtime: Runtime) => context.child(runtime.uniqueName) match {
				case None =>
					// Not this runtime admin actor, try the one mentioned in the runtime
					val message = s"""Cannot delete runtime "${runtime.uniqueName}" because the runtime does not exist."""
					log.error(message)
					val answer = Future.successful(
						("action" -> "Delete runtime") ~
						("name" -> runtime.uniqueName) ~
						("success" -> false) ~
						("reason" -> message))
					answer pipeTo originalSender
					answer
				case Some(actor) =>
					val future = actor.ask(RuntimeActor.DeleteRuntime())

					future onComplete {
						case Success(answer) =>
							log.info( s"""Child with name "${runtime.uniqueName}" removed""")
						case Failure(ex) =>
							log.error(ex.getMessage)
					}

					val answer = future.asInstanceOf[Future[JObject]]
					answer pipeTo originalSender
					answer
			}
		)
	}

	/**
	 * Gets the definition of a single runtime
	 * from the corresponding runtime actor.
	 * @param runtime The runtime to get the info for
	 * @return A future with the JSON representation of that runtime.
	 */
	def getRuntimeInfo(runtime: Runtime): Future[JObject] = {
		val originalSender = sender()

		forwardOrHandle(runtime, GetRuntimeInfo(runtime), Some(originalSender), (runtime: Runtime) =>
			context.child(runtime.uniqueName) match {
				case None =>
					val message = s"""Cannot get runtime info for "${runtime.uniqueName}" because the runtime does not exist."""
					log.error(message)
					val answer = Future.successful(
						("action" -> "Get runtime info") ~
							("name" -> runtime.uniqueName) ~
							("success" -> false) ~
							("reason" -> message))
					answer pipeTo originalSender
					answer
				case Some(child) =>
					val answer = child.ask(RuntimeActor.GetRuntimeInfo())
						.asInstanceOf[Future[JObject]]
					answer pipeTo originalSender
					answer
			})
	}

	/**
	 * Get all actors from a given runtime.
	 * @param runtime The runtime to get the actors from
	 * @return A future with the "actors" part of the JSON definition.
	 */
	def getActors(runtime: Runtime) {
		val originalSender = sender()

		forwardOrHandle(runtime,
			GetFromDefinition(runtime, "actors"), Some(originalSender),
			(runtime: Runtime) => getFromDefinition(runtime, "actors"))
	}

	/**
	 * Extract a value from the definition of an actor.
	 * The value can be either "actors" or "links".
	 * @param runtime The runtime to get the value from.
	 * @param value The value, can be "actors" or "links".
	 * @return The JObject subobject from the runtime definition JSON.
	 */
	def getFromDefinition(runtime: Runtime, value: String): Future[JObject] = {
		val originalSender = sender()

		forwardOrHandle(runtime, GetFromDefinition(runtime, value), Some(originalSender), (runtime: Runtime) => {
			val runtimeInfo = getRuntimeInfo(runtime)
			runtimeInfo.map((info: JObject) => {
				val success = (info \ "success").extractOpt[Boolean] match {
					case None =>
						log.error("Retrieved empty runtime info. This probably means that the runtime is not " +
							"finished initializing before the runtime definition was obtained.")
						false
					case Some(s) => s
				}

				if (success) {
					val extracted = (info \ value).extractOpt[JValue]

					extracted match {
						case None =>
							log.error(s"""Requested field "$value" not present in definition of runtime "${runtime.uniqueName}"""")
							JObject()
						case Some(r) =>
							(value -> (r \ value).extract[JValue]).asInstanceOf[JObject]
					}
				} else {
					JObject()
				}
			})
		})
	}

	/**
	 * Update runtime settings. Right now, this is only starting or stopping a runtime.
	 * @param runtime The runtime to update the settings for
	 * @param json The JSON object containing the settings.
	 * @return A future JObject containing the result of the operation.
	 */
	def updateRuntimeSettings(runtime: Runtime, json: JObject) {
		startOrStopRuntime(runtime, json)
	}

	/**
	 * Start or stop a runtime with a given name.
	 * @param runtime The runtime to start or stop.
	 * @param json The JSON object containing whether to start or to stop the runtime
	 * @return A future JObject containing the result of the operation.
	 */
	def startOrStopRuntime(runtime: Runtime, json: JObject) {
		val originalSender = sender()
		val action = (json \ "status").extractOpt[String]

		if (!action.contains ("start") && !action.contains("stop")) {
			val message = "Invalid action provided for runtime status change"
			log.error(message)
			Future.successful(
				("action" -> action) ~
				("name" -> runtime.uniqueName) ~
				("success" -> false) ~
				("reason" -> message)) pipeTo originalSender
		} else {
			action match {
				case Some("start") =>
					startRuntime(runtime) pipeTo originalSender
				case Some("stop") =>
					stopRuntime(runtime) pipeTo originalSender
				case _ =>
			}
		}
	}

	/**
	 * Starts a runtime.
	 * @param runtime The runtime to start
	 */
	def startRuntime(runtime: Runtime): Future[JObject] = {
		val originalSender = sender()

		forwardOrHandle(runtime, StartRuntime(runtime), Some(originalSender),
			(runtime: Runtime) => context.child(runtime.uniqueName) match {
				case None =>
					val message = s"""Cannot start runtime '${runtime.uniqueName}' because the runtime does not exist."""
					log.error(message)
					val msg =
						("action" -> "Start runtime") ~
						("name" -> runtime.uniqueName) ~
						("success" -> false) ~
						("reason" -> message)
					originalSender ! msg
					Future.successful(msg)
				case Some(child) =>
					val answer = child.ask(RuntimeActor.StartRuntime()).asInstanceOf[Future[JObject]]

					answer onComplete {
						case Success(_) => authenticator ! Invalidate()
						case _ =>
					}

					answer pipeTo originalSender
					answer
			})
	}

	/**
	 * Stops a runtime with a given name.
	 * @param runtime The runtime to stop
	 */
	def stopRuntime(runtime: Runtime): Future[JObject] = {
		val originalSender = sender()

		forwardOrHandle(runtime, StopRuntime(runtime), Some(originalSender),
			(runtime: Runtime) => context.child(runtime.uniqueName) match {
				case None =>
					val message = s"""Cannot stop runtime "${runtime.uniqueName}" because the runtime does not exist."""
					log.error(message)
					val msg =
						("action" -> "Stop runtime") ~
						("name" -> runtime.uniqueName) ~
						("success" -> false) ~
						("reason" -> message)
					originalSender ! msg
					Future.successful(msg)
				case Some(child) =>
					val answer = child.ask(RuntimeActor.StopRuntime()).asInstanceOf[Future[JObject]]
					answer pipeTo originalSender
					answer
			})
	}
}