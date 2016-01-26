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
import akka.persistence.{SaveSnapshotSuccess, RecoveryCompleted, PersistentActor, SnapshotOffer}
import akka.util.Timeout
import io.coral.actors.CoralActor.{Shunt, RegisterActor}
import io.coral.actors.connector.KafkaConsumerActor.{StopReadingMessageQueue, ReadMessageQueue}
import io.coral.actors.transform.{StopGenerator, StartGenerator}
import io.coral.api.security.Authenticator.{InvalidationFailed, InvalidationComplete, Invalidate}
import io.coral.api.security.{AddOwnerPermission, RemoveOwnerPermission}
import io.coral.api.{RuntimeStatistics, CoralConfig, Runtime}
import io.coral.utils.{Utils, NameSuggester}
import org.json4s._
import akka.actor._
import scaldi.Injector
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.concurrent.{ExecutionContext, Future}
import akka.pattern.ask
import scala.concurrent.duration._
import akka.pattern.pipe

// Put in an object because name collision
// with AdminActor would occur otherwise
object RuntimeActor {
	case class CreateRuntime(json: JObject, uniqueName: String)
	case class GetActorPath(name: String)
	case class DeleteActor(name: String)
	case class DeleteAllActors()
	case class StartRuntime()
	case class StopRuntime()
	case class DeleteRuntime()
	case class GetRuntimeInfo()
	case class GetRuntimeStatistics()
	case class GetActorStatistics(actorName: String)
	case class Shutdown()
}

/**
 * The RuntimeActor handles one specific runtime. It manages all
 * actors in the runtime, it starts the runtime and stops the
 * runtime. The state of this actor is just the statistics it
 * keeps for all child actors. This state is saved in snapshots.
 */
class RuntimeActor(uniqueName: String, owner: UUID)(implicit injector: Injector, ec: ExecutionContext)
	extends PersistentActor with ActorLogging {
	import RuntimeActor._
	def actorRefFactory = context
	implicit val formats = org.json4s.DefaultFormats
	implicit val timeout = Timeout(10.seconds)
	val config = new CoralConfig(context.system.settings.config)

	// All runtime properties defined in a single case class
	var runtime: Option[Runtime] = None
	override def persistenceId = uniqueName

	override def supervisorStrategy = SupervisorStrategies.logAndContinue(log)

	/**
	 * This method is executed when the RuntimeActor is restored
	 * from disk. Only replays events that are handled by doReplay.
	 */
	def receiveRecover: Receive = {
		// Set the latest runtime statistics in the case this
		// actor gets offered a snapshot
		case SnapshotOffer(_, snapshot: Runtime) =>
			log.info("RuntimeActor received snapshot: " + snapshot)
			runtime = Some(snapshot)
		// When replaying the event log, only replay the messages
		// that need to be replayed
		case event =>
			if (doReplay.isDefinedAt(event)) {
				log.info("Replaying event from event source: " + event.toString)
			}

			(doReplay orElse ignore)(event)
	}

	def ignore: Receive = {
		case _ =>
	}

	/**
	 * The main receiveCommand loop that receives commands and persists them.
	 * Combines both handlers below.
	 */
	def receiveCommand: Receive = {
		// First persist the message, then call the handler
		case message =>
			log.info("Persisting message " + message.toString)
			persist(message)(doReplay orElse doNotReplay)
	}

	/**
	 * Receive handler that handles all commands that need to be replayed
	 * when the runtime is restored from the event source.
	 */
	def doReplay: Receive = {
		case RuntimeActor.CreateRuntime(json, uniqueName) =>
			createRuntime(json, uniqueName)
		case StartRuntime() =>
			startRuntime()
		case StopRuntime() =>
			stopRuntime()
		case DeleteRuntime() =>
			val originalSender = sender()
			val result = deleteRuntime()
			context.children.foreach(_ ! Shutdown())
			originalSender ! result
			context.stop(self)
		case DeleteActor(name: String) =>
			context.child(name).foreach {
				child => actorRefFactory.actorSelection(child.path) ! PoisonPill
			}
		case DeleteAllActors() =>
			context.children.foreach {
				child => actorRefFactory.actorSelection(child.path) ! PoisonPill
			}
		case RecoveryCompleted =>
			// Ignore this message on purpose
		case SaveSnapshotSuccess(metadata) =>
			// Ignore this message on purpose
	}

	/**
	 * Receive handler that handles all commands that do NOT need to be replayed
	 * when the runtime is restored from the event source. These are typically
	 * events that do not have an impact on the state of the runtime, such
	 * as getters.
	 */
	def doNotReplay: Receive = {
		case GetRuntimeInfo() =>
			getRuntimeInfo()
		case GetRuntimeStatistics() =>
			getRuntimeStatistics()
		case GetActorPath(actorName) =>
			sender ! getActorPath(actorName)
		case RuntimeActor.GetActorStatistics(path) =>
		case InvalidationComplete() =>
		case InvalidationFailed() =>
		case _ =>
	}

	/**
	 * Gets the complete path belonging to an actor with a given name.
	 * Tries to lookup the actor with the given name in the children of this actor.
	 * @param actorName The name of the actor to look up.
	 * @return Some(path) of the actor, if it is a child of this actor, else None.
	 */
	def getActorPath(actorName: String): Option[ActorPath] = {
		context.child(actorName) match {
			case None => None
			case Some(c) => Some(c.path)
		}
	}

	/**
	 * Instantiate the actors and links based on a JSON definition.
	 * @param json The JSON object to read the definition from.
	 *             In this method, the JSON definition is assumed to
	 *             be fully valid since it has already been checked
	 *             by AdminActor.
	 * @param uniqueName The unique name ("ownerName-runtimeName") of the runtime.
	 *                   This uniquely identifies the runtime across the whole platform.
	 */
	def createRuntime(json: JObject, uniqueName: String) {
		val originalSender = sender()
		val permissionHandler = context.actorSelection("/user/root/authenticator/permissionHandler")
		val cassandra = context.actorSelection("/user/root/cassandra")

		try {
			log.info("Creating new runtime from definition " + compact(render(json)))
			val name = (json \ "name").extract[String]
			val owner = UUID.fromString((json \ "owner").extract[String])

			val runtimeId = UUID.randomUUID()
			val projectId = Utils.tryUUID((json \ "projectid").extractOpt[String])

			log.info( s"""Creating runtime with name "$name" and owner "$owner"""")
			val actors = (json \ "actors").extract[JArray]
			val links = (json \ "links").extract[JArray]

			log.info("Creating child actors...")
			actors.children.foreach(json => {
				createActor(json.asInstanceOf[JObject])
			})

			log.info("Creating links...")
			links.children.foreach(link => {
				createLink(link.asInstanceOf[JObject])
			})

			// status 0 = created
			val status = 0
			val startTime = System.currentTimeMillis

			val completePath = akka.serialization.Serialization.serializedActorPath(context.parent)
			val adminPath = completePath.substring(0, completePath.lastIndexOf("#"))

			this.runtime = Some(Runtime(
				runtimeId,
				owner,
				name,
				uniqueName,
				adminPath,
				status,
				projectId,
				json,
				None,
				startTime
			))

			insertRuntimeInTable(cassandra, runtime)
			permissionHandler ! AddOwnerPermission(this.runtime)

			log.info("Successfully created runtime.")

			originalSender !
				("success" -> true) ~
				("created" -> Utils.friendlyTime(startTime)) ~
				("id" -> runtimeId.toString) ~
				("definition" -> json)
		} catch {
			case e: Exception =>
				log.error(e.getMessage)

				markRuntimeDeleted(this.runtime)
				permissionHandler ! RemoveOwnerPermission(this.runtime)

				originalSender !
					("success" -> false) ~
					("reason" -> e.getMessage) ~
					("details" -> e.getStackTrace.mkString("\n"))
		}
	}

	/**
	 * Get the runtime info of this runtime.
	 * This info contains the ID, the status, the
	 * start time and the definition of the runtime.
	 */
	def getRuntimeInfo() {
		val result = this.runtime match {
			case None =>
				("action" -> "Get runtime info") ~
				("name" -> uniqueName) ~
				("success" -> false) ~
				("reason" -> "Cannot get runtime info because the runtime is not defined")
			case Some(r) =>
				("id" -> r.id.toString) ~
				("status" -> r.status) ~
				("startTime" -> Utils.friendlyTime(r.startTime)) ~
				("definition" -> r.jsonDef)
		}

		sender ! result
	}

	/**
	 * Return runtime statistics for this runtime. These include the number
	 * of actors in the runtime, the number of processed messages and the
	 * number of exceptions thrown, among other data.
	 * @return A JSON object containing statistics for this runtime.
	 */
	def getRuntimeStatistics() {
		val originalSender = sender()

		try {
			val futureList = Future.sequence(context.children.map(child =>
				child.ask(CoralActor.GetActorStatistics())))
			val merged = futureList.map(result => RuntimeStatistics.merge(result
				.asInstanceOf[List[RuntimeStatistics]]))
			merged.map(RuntimeStatistics.toJson(_)) pipeTo originalSender
		} catch {
			case e: Exception =>
				log.error(e.getMessage)
				originalSender ! List()
		}
	}

	/**
	 * Execute a CQL statement if a runtime is defined. Else, execute an alternative statement.
	 * @param runtime The runtime Option to check if it is defined or not.
	 * @param cassandra The cassandra driver to execute the statement on, if the runtime is defined.
	 * @param ifPart This part is executed when the runtime is defined.
	 * @param elsePart This part is executed when the runtime is not defined.
	 */
	def executeStmtIfDefined(runtime: Option[Runtime], cassandra: ActorSelection,
							 ifPart: Runtime => String, elsePart: => Unit) {
		runtime match {
			case Some(r) =>
				val stmt = ifPart(r)
				val json: JObject = ("query" -> stmt)
				cassandra ! Shunt(json)
			case None =>
				elsePart
		}
	}

	/**
	 * Insert a runtime in the runtimes table (Used when the runtime is first created).
	 * @param cassandra The cassandra actor to send the CQL statement to.
	 * @param runtime The definition of the runtime, if any.
	 */
	def insertRuntimeInTable(cassandra: ActorSelection, runtime: Option[Runtime]) {
		log.info("Inserting runtime in runtimes table...")

		// When JSON strings contain a single quote character ('), Cassandra cannot insert
		// it into the database. Therefore, escape them using twice the single quote character ('').
		executeStmtIfDefined(runtime, cassandra, r => {
				s"""insert into """ +
				s"""${config.coral.cassandra.keyspace}.${config.coral.cassandra.runtimeTable} """ +
				s"""(id, owner, name, adminpath, status, projectid, jsondef, startedon) values """ +
				s"""(${r.id.toString}, ${r.owner}, '${r.name}', '${if (!config.coral.cluster.enabled)
					"local" else r.adminPath}', ${r.status}, ${r.projectId.getOrElse("null")}, """ +
				s"""'${compact(render(r.jsonDef)).replace("'", "''")}', ${r.startTime});"""
			}, log.error("Cannot insert runtime in table since the runtime is not defined."))
	}

	/**
	 * Create a new actor based on a JSON definition.
	 * @param json The JSON definition to create an actor from.
	 */
	def createActor(json: JObject) {
		log.info("Creating new actor from json " + compact(render(json)))
		val name = getName(json, context)
		val props = CoralActorFactory.getProps(json)
		val jsonStr = compact(render(json))

		props match {
			case None =>
				log.error("Could not create actor props from JSON " + jsonStr)
			case Some(p) =>
				context.actorOf(p, name)
				log.info("Succesfully created new actor.")
		}
	}

	/**
	 * Create a link between two existing actors.
	 * Since input validation was already performed by
	 * the RuntimeAdminActor, we assume that the input
	 * is fully valid here.
	 * @param json The JSON definition of the link,
	 *             containing two actor names: from and to.
	 */
	def createLink(json: JObject) {
		val fromName = (json \ "from").extract[String]
		val toName = (json \ "to").extract[String]

		if (!context.child(fromName).isDefined) {
			throw new IllegalArgumentException(fromName)
		}

		if (!context.child(toName).isDefined) {
			throw new IllegalArgumentException(toName)
		}

		log.info(s"""Creating link between actor "$fromName" and "$toName"""")

		val from = context.child(fromName).get
		val to = context.child(toName).get

		from ! RegisterActor(to)
	}

	/**
	 * Gets the name of an actor from a JSON object.
	 * If the name already exists, automatically appends
	 * a number to it, starting with 2. If it already
	 * contains a number at the end of the string,
	 * this number is incremented by 1.
	 * @param json The JSON actor object to get the name from
	 * @return The new name of the actor.
	 */
	def getName(json: JObject, context: ActorContext): String = {
		val proposed = (json \ "name").extract[String]
		NameSuggester.suggestName(proposed, context.children.map(_.path.name).toList)
	}

	/**
	 * Instruct all Kafka listeners to start listening
	 * and instruct all generator actors to start generating.
	 * Because JSON is guaranteed to be valid here, no need to
	 * worry about type and name not existing.
	 */
	def startRuntime() {
		val originalSender = sender()
		log.info(s"""Starting runtime "$uniqueName"""")

		this.runtime match {
			case None =>
				val message = "Cannot start runtime because the runtime is not defined."
				log.error(message)
				val msg =
					("action" -> "Start runtime") ~
					("name" -> uniqueName) ~
					("success" -> false) ~
					("reason" -> message)
				originalSender ! msg
			case Some(r) =>
				log.info(s"""Starting runtime "$uniqueName"""")

				// Can only start a runtime which is created or stopped
				if (r.status == 0 || r.status == 2) {
					// The only actors we need to start are kafka consumers and generators
					val allConsumers = getAllChildren("kafka-consumer")
					allConsumers.foreach(_ ! ReadMessageQueue())

					val allGenerators = getAllChildren("generator")
					allGenerators.foreach(_ ! StartGenerator())

					changeRuntimeState(this.runtime, 1)

					val ownerName = uniqueName.split("-")(0)
					val runtimeName = uniqueName.split("-")(1)

					originalSender !
						("action" -> "Start runtime") ~
						("name" -> runtimeName) ~
						("owner" -> ownerName) ~
						("success" -> true) ~
						("time" -> Utils.friendlyTime(System.currentTimeMillis))
				} else {
					originalSender !
						("action" -> "Start runtime") ~
						("success" -> false) ~
						("reason" -> "Cannot start a runtime with a status other than created or stopped.")
				}
		}
	}

	/**
	 * Instruct all Kafka listeners to stop listening
	 * and instruct all generator actors to stop generating
	 */
	def stopRuntime() {
		val originalSender = sender()

		this.runtime match {
			case None =>
				val message = "Cannot stop runtime because the runtime is not defined."
				log.error(message)
				val msg = ("action" -> "Stop runtime") ~
					("name" -> uniqueName) ~
					("success" -> false) ~
					("reason" -> message)
				originalSender ! msg
			case Some(r) =>
				log.warning(s"""Stopping runtime "$uniqueName"""")

				// Can only stop a runtime that is actually running
				if (r.status == 1) {
					log.info("Stopping all kafka consumers...")
					val allConsumers = getAllChildren("kafka-consumer")
					log.info(s"Obtained a list of ${allConsumers.size} kafka consumer actors.")
					allConsumers.foreach(_ ! StopReadingMessageQueue())

					log.info("Stopping all generator actors...")
					val allGenerators = getAllChildren("generator")
					log.info(s"Obtained a list of ${allGenerators.size} generator actors.")
					allGenerators.foreach(_ ! StopGenerator())

					changeRuntimeState(this.runtime, 2)

					val msg =
				 		("action" -> "Stop runtime") ~
						("success" -> true) ~
						("name" -> uniqueName) ~
						("time" -> Utils.friendlyTime(System.currentTimeMillis))

					originalSender ! msg
				} else {
					val msg =
						("action" -> "Stop runtime") ~
						("success" -> true) ~
						("name" -> uniqueName) ~
						("reason" -> "Cannot stop a runtime that is not running.")

					originalSender ! msg
				}
		}
	}

	/**
	 * Change the state of a runtime to a new state.
	 * This encompasses three things:
	 * 1) Update the runtime object in this actor with the new state
	 * 2) Save a snapshot of the change to the snapshot store
	 * 3) Update the new state in the runtimes table
	 * @param runtime The runtime of which the state should be changed.
	 * @param newStatus The new state to set
	 */
	def changeRuntimeState(runtime: Option[Runtime], newStatus: Int) {
		runtime match {
			case None =>
				log.error("Cannot change runtime state for nonexisting runtime")
			case Some(r) =>
				this.runtime = Some(r.copy(status = newStatus, startTime = System.currentTimeMillis))

				saveSnapshot(this.runtime)

				newStatus match {
					case 1 => markRuntimeStarted(this.runtime)
					case 2 => markRuntimeStopped(this.runtime)
					case 4 => markRuntimeDeleted(this.runtime)
				}
		}
	}

	/**
	 * Mark a runtime as started.
	 * @param runtime The runtime which should be marked as started.
	 */
	def markRuntimeStarted(runtime: Option[Runtime]) {
		log.info("Marking runtime as started")
		updateRuntimeStatusInTable(runtime, 1,
			log.error("Cannot mark runtime as started in the table since the runtime is not defined."))
	}

	/**
	 * Mark a runtime as stopped.
	 * @param runtime The runtime which should be marked as stopped.
	 */
	def markRuntimeStopped(runtime: Option[Runtime]) {
		log.info("Marking runtime as stopped")
		updateRuntimeStatusInTable(runtime, 2,
			log.error("Cannot mark runtime as stopped in the table since the runtime is not defined."))
	}

	/**
	 * Mark a runtime in the runtime table as deleted.
	 * @param runtime The runtime to remove from the runtime definition table.
	 */
	def markRuntimeDeleted(runtime: Option[Runtime]) {
		log.warning("Marking runtime as deleted")
		updateRuntimeStatusInTable(runtime, 4,
			log.error("Cannot mark the runtime as deleted since the runtime is not defined."))
	}

	/**
	 * Update the status of a runtime in the runtime table.
	 * @param runtime The runtime, if any, to update the status of.
	 * @param status The status to set in the table.
	 * @param elsePart Is executed when the runtime is not defined.
	 */
	def updateRuntimeStatusInTable(runtime: Option[Runtime], status: Int, elsePart: => Unit) = {
		val cassandra = context.actorSelection("/user/root/cassandra")

		executeStmtIfDefined(runtime, cassandra, r => {
			s"""update ${config.coral.cassandra.keyspace}.${config.coral.cassandra.runtimeTable} """ +
				s"""set status = $status """ +
				s"""where id = ${r.id.toString} if exists;"""
		}, elsePart)

		// Invalidate the CoralAuthenticator so that it can update the runtime states
		val authenticator = context.actorSelection("/user/root/authenticator")
		authenticator ! Invalidate()
	}

	/**
	 * Fully deletes the runtime, stops all actors in it
	 * and kills and deletes all actors.
	 *
	 * If permanent, all messages are actually removed from the
	 * journal table. If not permanent, all messages are simply
	 * marked as deleted and will not be replayed the next time
	 * an actor is started with the same name, but these messages
	 * will remain present in the journal table.
	 */
	def deleteRuntime(): JObject = {
		log.warning(s"""Deleting runtime "$uniqueName"""")

		val cassandra = context.actorSelection("/user/root/cassandra")

		log.info("Deleting runtime from table")
		markRuntimeDeleted(this.runtime)

		log.info("Deleting owner permission from authorization table")
		val authenticator = context.actorSelection("/user/root/authenticator")
		authenticator ! RemoveOwnerPermission(this.runtime)

		("action" -> "Delete runtime") ~
		("name" -> uniqueName) ~
		("success" -> true) ~
		("time" -> Utils.friendlyTime(System.currentTimeMillis))
	}

	/**
	 * Gets all children of a certain type.
	 * @param actorType The type of the actor, as used
	 *                  in the coral actor factories.
	 * @return A list of actor references with this actor type.
	 */
	def getAllChildren(actorType: String): List[ActorRef] = {
		val allChildren = (this.runtime.get.jsonDef \ "actors").children

		val properType: List[JValue] = allChildren.filter(actor => {
			((actor \ "type").extract[String] == actorType)
		})

		val names = properType.map(child => (child \ "name").extract[String])
		val areDefined = names.filter(name => context.child(name).isDefined)
		areDefined.map(name => context.child(name).get)
	}
}