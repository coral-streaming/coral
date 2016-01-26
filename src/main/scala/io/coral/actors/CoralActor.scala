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

import io.coral.actors.CoralActor._
import io.coral.api.RuntimeStatistics
import scala.collection.immutable.{SortedMap, SortedSet}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import spray.util.actorSystem
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._

object CoralActor {
	case class GetActorStatistics()
	case class Shutdown()
	case class Shunt(json: JObject)
	case class GetField(field: String)
	case class ListFields()
	case class RegisterActor(r: ActorRef)
	case class UpdateProperties(json:JObject)
	case class GetProperties()
}

/**
 * Abstract Coral actor class. Represents base functionality
 * of all Coral actors that communicate through JSON messages.
 * @param json The JSON constructor of the actor.
 */
abstract class CoralActor(json: JObject)
	extends Actor
	with NoTrigger
	with NoTimer
	with ActorLogging {
	implicit def executionContext: ExecutionContextExecutor = actorRefFactory.dispatcher
	implicit val timeout = Timeout(1000.milliseconds)
	implicit val formats = org.json4s.DefaultFormats

	def actorRefFactory = context
	def jsonDef = json
	def askActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).ask(msg)
	def tellActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).!(msg)
	def in[U](duration: FiniteDuration)(body: => U): Unit =
		actorSystem.scheduler.scheduleOnce(duration)(body)

	// Defines the state of this actor, which is a map of (variable name, JSON object).
	def state: Map[String, JValue] = noState
	// The targets to which the actor will emit its trigger result
	var emitTargets = SortedSet.empty[ActorRef]
	// The collect definition, if any
	val collectDef: Option[CollectDef] = CollectDef.get(json)

	// Counter objects to track number of messages etc.
	var stats = new Counter()

	// Children of this actor
	var children = SortedMap.empty[String, Long]
	// Default empty state
	val noState: Map[String, JValue] = Map.empty

	override def preStart() {

	}

	override def receive = baseReceive orElse receiveExtra

	/**
	 * The main receive loop of a Coral Actor.
	 */
	def baseReceive: Receive = {
		case json: JObject =>
			process(json)
		case Shunt(json) =>
			shunt(json)
		case GetField(x) =>
			stateResponse(x, None, sender())
		case RegisterActor(r) =>
			emitTargets += r
		case Shutdown() =>
			context.stop(self)
		case GetActorStatistics() =>
			getStats()
	}

	/**
	 * Extra method to enable actors to receive messages
	 * outside of JSON objects. Only used internally and
	 * not exposed to the API. Overriden by child actors.
	 */
	def receiveExtra: Receive = {
		case Unit =>
	}

	/**
	 * Process a JSON object. This is the main handler that
	 * handles all trigger JSON messages.
	 */
	def process(json: JObject) = {
		// Only emit the result to emit targets
		stats.increment("json")
		execute(trigger(json), None)
	}

	/**
	 * Process a JSON object and return the result to
	 * the sender. Only used in special cases where
	 * the JSON object is wrapped in a "Shunt" object.
	 */
	def shunt(json: JObject) = {
		// Sends back the result to the sender
		// This is needed for "hybrid" actors, such as the CassandraActor
		stats.increment("shunt")
		execute(trigger(json), Some(sender()))
	}

	/**
	 * Collect a JSON value from an actor with a given name.
	 * The actor name must be present in the same runtime as this actor.
	 * The type parameter "A" is the type of the object that should be returned:
	 * collect[Double]("field") returns a Double value.
	 * The alias refers to the alias as specified in the JSON collect definition,
	 * and has a fixed name that is stated in the documentation of the actor.
	 * @param alias The name of the field to extract, as defined by the CoralActor itself.
	 * @param mf The type of object to return. For instance, collect[Double]("avg")
	 *           will return a Double object.
	 * @param timeout The timeout after which the query is assumed to have failed.
	 *                50 milliseconds is subtracted from this time to actually perform
	 *                the query because this actor must have time to respond as well.
	 * @return A future with the JSON object in it.
	 */
	def collect[A](alias: String)(implicit mf: Manifest[A], timeout: Timeout): Future[A] = {
		collectDef match {
			case None =>
				val message = "Using collect method without collect definition."
				log.error(message)
				Future.failed(throw new Exception(message))
			case Some(c) =>
				// First try field definitions, then json definitions.
				if (c.fields.isDefinedAt(alias)) {
					queryField(c, alias)
				} else if (c.jsons.isDefinedAt(alias)) {
					queryJSON(c, alias).asInstanceOf[Future[A]]
				} else {
					Future.failed(throw new Exception(s"Collect can not find field or JSON definition '$alias'."))
				}
		}
	}

	/**
	 * Query a "static" field of another CoralActor. A static field is a value that
	 * the actor exposes in its state map. No parameters can be passed to this actor,
	 * it just fetches the value it finds with that name in the state of the actor.
	 * @param collectDef The collect definition of this actor
	 * @param alias The alias under which the field is known with the other actor.
	 * @param mf The type of object to return.
	 * @return A future holding the object requested.
	 */
	def queryField[A](collectDef: CollectDef, alias: String)(implicit mf: Manifest[A]): Future[A] = {
		collectDef.fields.get(alias) match {
			case None =>
				val message = s"Collect alias '$alias' not found in static field queries."
				Future.failed(throw new Exception(message))
			case Some((from, field)) =>
				log.info(s"Collecting static field '$field' from actor '$from'.")

				// The other actor must be a sibling of this actor
				val path = "../" + from
				val future = recoveredFuture(path, GetField(field))

				future.map(answer => {
					val extracted = (answer \ field).extractOpt[A]
					extracted match {
						case None =>
							val message = s"Can not extract field '$field' from actor '$from'."
							throw new Exception(message)
						case Some(result) =>
							result
					}
				})
		}
	}

	/**
	 * Performs a "dynamic" query on another CoralActor. It does this by passing the JSON object
	 * to the other query wrapped in a "Shunt" object. This way, the other CoralActor knows that
	 * he should return the object to the asker.
	 * @param collectDef The collect definition to get the alias from.
	 * @param alias The alias to get the query definition from.
	 * @param message The message to send to the actor
	 * @return A Future with the JSON object returned from the actor.
	 */
	def queryJSON(collectDef: CollectDef, alias: String): Future[JObject] = {
		collectDef.jsons.get(alias) match {
			case None =>
				val message = s"Collect alias '$alias' not found in JSON queries."
				Future.failed(throw new Exception(message))
			case Some((from, json)) =>
				log.info(s"Collecting JSON query '${compact(render(json))}' from actor '$from'.")
				val path = "../" + from
				recoveredFuture(path, Shunt(json))
		}
	}

	/**
	 * Create a future based on a question to an actor that, if not answered in
	 * time, will return a Future.failed.
	 * @param path The path to send the message to
	 * @param msg The message to send the actor
	 * @return A future JSON object (if it succeeds).
	 */
	def recoveredFuture(path: String, msg: Any): Future[JObject] = {
		context.actorSelection(path).ask(msg)(
			// Give the collect actor less time to respond than the total timeout
			// because otherwise this answer has no time to reach the asker
			timeout.duration - 50.millis).asInstanceOf[Future[JObject]] recoverWith {
				// This is the case if the actor fails to respond in time
				case _ =>
					return Future.failed(throw new Exception("Collect actor failed to respond in time."))
			}
		}

	/**
	 * Emit a JSON value to any of its emit targets.
	 * If the object is not a JObject, it will not be sent.
	 */
	def emit(json: JValue) = {
		json match {
			case json: JObject =>
				emitTargets map (actorRef => actorRef ! json)
			case _ =>
		}
	}

	/**
	 * Execute a trigger. If the sender is given, return the result to the
	 * sender (in case of a Shunt). Otherwise, send the result to its emit targets.
	 * @param future The future to process.
	 * @param sender Some(actor) in case of a Shunt, None otherwise.
	 */
	def execute(future: Future[Option[JValue]], sender: Option[ActorRef]) = {
		future.onSuccess {
			case Some(result) =>
				sender match {
					// When None, emit the result normally
					case None => emit(result)
					// When Some(s), it is a shunt so only return result to sender
					case Some(s) => s ! result
				}
			case None =>
				stats.increment("failure")
				log.warning("not processed")
		}

		future.onFailure {
			case ex =>
				stats.increment("failure")
				log.error(ex, "actor execution")
		}
	}

	/**
	 * Get actor statistics as collected in the stats counter object.
	 * @return A list with (runtime name, stat name, stat value) tuples.
	 */
	def getStats() {
		val originalSender = sender()
		val actorName = self.path.name

		val otherStats = stats.toMap
				.filterKeys(k => k != "json" && k != "failure")
				// Add actor name to map
				.map(x => (actorName, x._1) -> x._2)

		val result = RuntimeStatistics(1,
			stats.get("json"),
			stats.get("failure"),
			otherStats)

		originalSender ! result
	}

	/**
	 * Respond to a state request.
	 * @param x The name of the state object.
	 */
	def stateResponse(x: String, by: Option[String], sender: ActorRef) = {
		if (by.getOrElse("").isEmpty) {
			val value = state.get(x)

			value match {
				case None => sender ! JNothing
				case v => sender ! render(x -> v)
			}
		} else {
			val found = children.get(by.get) flatMap (a => actorRefFactory.child(a.toString))

			found match {
				case Some(actorRef) =>
					actorRef forward GetField(x)
				case None =>
					sender ! render(JNothing)
			}
		}
	}
}