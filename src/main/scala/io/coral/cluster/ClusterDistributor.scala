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

package io.coral.cluster

import java.util.UUID
import akka.actor._
import akka.cluster.Member
import akka.util.Timeout
import io.coral.actors.RuntimeAdminActor
import io.coral.api.security.AuthInfo
import io.coral.api.{Runtime, CoralConfig}
import io.coral.api.security.Authenticator._
import io.coral.cluster.ClusterDistributor.{ResetRoundRobin, RestartRuntimes, InvalidateAllAuthenticators, CreateRuntimeLocally}
import io.coral.cluster.ClusterMonitor.{GetPlatformStatistics, GetAddresses}
import io.coral.utils.Utils
import org.json4s._
import org.uncommons.maths.random.MersenneTwisterRNG
import scaldi.Injector
import scala.concurrent.{Await, ExecutionContext, Future}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.util.{Failure, Success}
import akka.pattern.ask
import scala.concurrent.duration._
import akka.pattern.pipe

object ClusterDistributor {
	// Asks the cluster distributor to create a
	// new runtime according to its strategy.
	case class CreateRuntime(uniqueName: String,
							 jsonDef: JObject, owner: UUID)

	// Asks the cluster distributor to create a new runtime
	// as a child of its own, right where it is running.
	case class CreateRuntimeLocally(uniqueName: String,
									jsonDef: JObject, owner: UUID)

	case class InvalidateAllAuthenticators()

	// Received when a cluster is unreachable,
	// according to the cluster monitor
	case class RestartRuntimes(member: Member)

	// Start one the first node again for round robin.
	// For testing purposes.
	case class ResetRoundRobin()
}

/**
 * Distributes a runtime across the cluster with a certain criterium.
 * In the case there is just one machine in the cluster, each of these
 * methods just starts the runtime on that machine. Otherwise, this
 * actor decides which machine(s) to distribute the runtime on according
 * to the setting in the .config file ("config.coral.distributor.mode").
 *
 * It also can answer questions about which runtime is running on which
 * machine, which is useful for restarting a machine on which runtimes
 * were running.
 *
 * The cluster distributor lives as a child of the RuntimeAdminActor.
 * Its path is thus always "/user/root/admin/clusterDistributor".
 */
class ClusterDistributor(implicit ec: ExecutionContext,
						 injector: Injector, config: CoralConfig) extends Actor with ActorLogging {
	// A distributor is a function that returns a list of
	// machines or a single machine from a given list of machines.
	type distributor = Future[List[Machine]] => Future[List[Machine]]
	// For round robin distribution
	var previousMachine: Option[Machine] = None
	// For random machine distribution
	val rand = new MersenneTwisterRNG()
	val clusterMonitor = context.actorSelection("/user/root/clusterMonitor")
	implicit val formats = org.json4s.DefaultFormats
	implicit val timeout = Timeout(5.seconds)

	override def preStart() = {

	}

	override def receive = {
		case InvalidateAllAuthenticators() =>
			invalidateAllAuthenticators()
		case ClusterDistributor.CreateRuntime(uniqueName, jsonDef, owner) =>
			createRuntime(uniqueName, jsonDef, owner)
		case RestartRuntimes(member: Member) =>
			restartRuntimes(member)
		case ResetRoundRobin() =>
			previousMachine = None
		case _ =>
	}

	/**
	 * Create a new runtime with the unique name, json definition and owner.
	 * @param uniqueName The unique name of the runtime
	 * @param jsonDef The JSON definition of the runtime.
	 * @param owner The owner of the runtime.
	 */
	def createRuntime(uniqueName: String, jsonDef: JObject, owner: UUID) {
		val originalSender = sender()

		log.info(s"""Valid runtime definition found. Delegating runtime creation of runtime "$uniqueName"""")

		// Determine where this runtime actor should be placed
		// and then send the request to the proper machine
		val future = distributeRuntime(uniqueName, jsonDef, owner)

		future onComplete {
			case Success(answer) =>
				if ((answer \ "success").extract[Boolean]) {
					log.info("Succesfully created runtime.")
				} else {
					log.error("Failed to create runtime.")
					//runtimeActor ! PoisonPill
				}
			case Failure(ex) =>
				log.error("clusterDistributor failed to start runtime")
				log.error(ex.getMessage())
		}

		future pipeTo originalSender
	}

	/**
	 * Distribute the runtime according to the distribution stategy as defined in
	 * the "distribution" section of the runtime JSON definition, or when this
	 * section is not defined, distribute it according to the settings in the
	 * CoralConfig object.
	 *
	 * First, a list of machines is obtained on which the runtime could start.
	 * This usually means all machines in the cluster.
	 * Then, look at the json definition of the runtime and run it according to the
	 * instructions in that definition. If not defined, then look at the instructions
	 * in the CoralConfig object.
	 *
	 * When a single machine is eventually returned to run the runtime on, the request
	 * is directed to the RuntimeAdminActor on that machine. A runtime can also be
	 * distributed across multipe machines, but currently that is not implemented yet.
	 * @param uniqueName The unique name of the runtime
	 * @param jsonDef The json definition of the runtime
	 * @param owner The owner UUID of the runtime
	 * @return A Future JSON object that contains the result of the operation.
	 */
	def distributeRuntime(uniqueName: String, jsonDef: JObject, owner: UUID): Future[JObject] = {
		try {
			val distributionSection = (jsonDef \ "distribution").extractOpt[JObject]

			if (config.coral.cluster.enabled) {
				val allMachines = getAllMachines()

				// The distribution section overrides the settings in the configuration file
				val machines = distributionSection match {
					case Some(config) =>
						log.info("Distribution section specified in runtime configuration. " +
							"Distributing runtime over specified machines.")
						getMachinesFromConfig(config, allMachines)
					case None =>
						log.info("No distribution section specified in runtime configuration. " +
							"Distributing runtime with mode specified in configuration file.")
						// The user has not specified distribution instructions.
						// Deploy it according to the given settings in the configuration
						distribute(config.coral.distributor.mode, allMachines)
				}

				distributeToMachines(machines, uniqueName, jsonDef, owner)
			} else {
				// Always run on the local machine, no matter what the configuration is
				log.info("Distributing to local machine since platform is running in nocluster mode.")
				distributeToMachines(Future.successful(List()), uniqueName, jsonDef, owner)
			}
		} catch {
			case e: Exception =>
				val message = e.getMessage
				log.error(message)
				Future.successful(
					("success" -> false) ~
					("reason" -> message))
		}
	}

	/**
	 * Get the list of machines from the config object and extract
	 * a machine from that definition. In the case of a predefined distribution,
	 * extract the machine from the JSON object. In all other cases,
	 * call the corresponding distribution function.
	 *
	 * Examples of distribution sections in the runtime JSON definition:
	 *
	 * "distribution": {
	 *    "mode": "predefined",
	 *    "machine": {
	 *       "ip": "127.0.0.1",
	 *       "port": 2551
	 *    }
	 * }
	 *
	 * OR
	 *
	 * "distribution": {
	 *    "mode": "local/round-robin/random/least-nr-actors/least-nr-runtimes/least-busy"
	 * }
	 *
	 * @return A future list of machines on which the runtime will
	 *         actually run. This can be a single machine or it can
	 *         be multiple machines. Multiple machine deploys are
	 *         currently not implemented yet.
	 */
	def getMachinesFromConfig(distribution: JObject, allMachines: Future[List[Machine]]): Future[List[Machine]] = {
		val mode = (distribution \ "mode").extract[String]

		mode match {
			case "predefined" =>
				Future {
					val ip = (distribution \ "machine" \ "ip").extractOpt[String]
					val port = (distribution \ "machine" \ "port").extractOpt[Int]

					if (!ip.isDefined || !port.isDefined) {
						log.error("Invalid predefined machine definition found")
						List()
					} else {
						List(Machine(None, ip.get, port.get, List(), None))
					}
				}
			case other =>
				distribute(mode, allMachines)
		}
	}

	/**
	 * Get the list of existing machines currently in the cluster
	 * from which a machine or multiple machines can be chosen.
	 */
	def getAllMachines(): Future[List[Machine]] = {
		if (config.coral.cluster.enabled) {
			clusterMonitor.ask(ClusterMonitor.GetClusterInfo())
				.asInstanceOf[Future[JObject]].map(info => {
				val success = (info \ "success").extractOpt[Boolean]

				if (success.isDefined && success.get == false) {
					// Running unit tests, config.coral.cluster.enable = true
					// and akka.actor.provider = "akka.actor.LocalActorRefProvider"
					List()
				} else {
					val members = (info \ "members").extract[JArray]

					val result = members.arr.filter(x => !(x \ "roles").extract[List[String]]
						.contains("debug-helper")).map(value => {
						val ip = (value \ "address" \ "ip").extract[String]
						val port = (value \ "address" \ "port").extract[Int]
						val roles = (value \ "roles").extract[List[String]]
						val status = (value \ "status").extractOpt[String]

						// Alias is not relevant here
						Machine(None, ip, port, roles, status)
					})

					result
				}
			})
		} else {
			// This can happen if coral.cluster.enable = true, but
			// akka.actor.provider = "akka.actor.LocalActorRefProvider".
			Future.successful(getLocalMachine())
		}
	}

	/**
	 * Distribute a runtime over a number of machines.
	 * @param futureMachines The future list of machines to start the runtime on.
	 * @param uniqueName The unique name of the runtime
	 * @param jsonDef The JSON definition of the runtime
	 * @param owner The owner of the runtime.
	 * @return A future JSON object containing the result of the distribution operation.
	 */
	def distributeToMachines(futureMachines: Future[List[Machine]], uniqueName: String,
							 jsonDef: JObject, owner: UUID): Future[JObject] = {
		futureMachines.flatMap(machines => {
			// See if it has returned 0 machines, 1 machine or multiple machines
			machines.size match {
				case 0 =>
					// This happens when config.coral.cluster.enable = false,
					// or in unit tests when akka.actor.provider = LocalActorRefProvider.
					val localAddress = "/user/root/admin"
					context.actorSelection(localAddress).ask(CreateRuntimeLocally(uniqueName, jsonDef, owner))
						.asInstanceOf[Future[JObject]]
				case 1 =>
					// Contact the cluster distributor on that machine,
					// and ask it to create a runtime there
					val m = machines(0)

					// Ask the RuntimeAdminActor on that machine to create a runtime locally over there
					val remoteAddress = s"akka.tcp://coral@${m.ip}:${m.port}/user/root/admin"
					log.info(s"""Delegating runtime creation to local actor with address "$remoteAddress""""
						+ s""" (currently on "${Utils.getFullSelfPath(self)}")""")
					val overThere = context.actorSelection(remoteAddress)

					overThere.ask(CreateRuntimeLocally(uniqueName, jsonDef, owner))
						.asInstanceOf[Future[JObject]]
				case n =>
					// It is a list of machines, returned by the "split..." methods.
					// Determine how to split the runtime now
					// ...
					Future.successful(JObject())
			}
		})
	}

	/**
	 * Fetches a list of machines on which the runtime should run, depending
	 * on the distribution mode specified.
	 * @param mode The distribution mode. Can be supplied through a "distribution"
	 *             section or through the CoralConfig object.
	 * @param m The future list of all machines to choose from.
	 * @return A selection from this list according to the criterium.
	 */
	def distribute(mode: String, m: Future[List[Machine]]): Future[List[Machine]] = {
		log.info(s"""Assembling list of machines based on distribution mode "$mode".""")

		mode match {
			// These return 1 machine:
			case "local" => local(m)
			case "round-robin" => roundRobin(m)
			case "least-nr-runtimes" => leastNrRuntimes(m)
			case "least-nr-actors" => leastNrActors(m)
			case "random" => random(m)
			case "least-busy" => leastBusy(m)
			// These return n machines:
			case "split-predefined" => splitPredefined(m)
			case "split-least-busy" => splitLeastBusy(m)
			case "split-random" => splitRandom(m)
			case "split-least-nr-runtimes" => splitLeastNrRuntimes(m)
			case "split-least-nr-actors" => splitLeastNrActors(m)
			case other => Future.successful(List())
		}
	}

	/**
	 * Invalidate all authenticator actors in the entire cluster.
	 * Returns a "combined" future with a single answer.
	 * If any of the separate nodes does not return true or does
	 * not return its answer in time, the result is Future(false).
	 */
	def invalidateAllAuthenticators() {
		val originalSender = sender()

		if (config.coral.cluster.enabled) {
			// Future[List[String]] => Future[List[Boolean]] => Future[Boolean]
			// future list of actors => future list of answers => future result
			clusterMonitor.ask(GetAddresses("authenticator")).flatMap(list => {
				val addresses = list.asInstanceOf[List[String]]

				log.info("Sending Invalidate message to following actors: " + addresses.toString)
				val clusterSize = addresses.size

				val future = Future.sequence(addresses.map(a => {
					context.actorSelection(a).ask(Invalidate())
				})) recover {
					// In case any invalidate fails to respond in time, it is a failure
					case _ => List(InvalidationFailed())
				}

				future.map(list =>
					if (list.contains(InvalidationFailed()) || list.size != clusterSize) {
						log.error(s"Failed to invalidate $clusterSize authenticators.")
						InvalidationFailed()
					} else {
						log.info(s"Succesfully invalidated $clusterSize authenticators.")
						InvalidationComplete()
					}) pipeTo originalSender
			})
		} else {
			// If running in nocluster mode, only invalidate local authenticator
			context.actorSelection("/user/root/authenticator").ask(Invalidate()) pipeTo originalSender
		}
	}

	/**
	 * When an "unreachable member" message is received, check if
	 * there were any runtimes running on that node. If that is the case,
	 * restart those runtimes on another node.
	 * @param member The member that has become unreachable.
	 */
	def restartRuntimes(member: Member) {
		val originalSender = sender()

		log.info("Restarting stopped runtimes after node crash")

		val authenticator = context.actorSelection("/user/root/authenticator")
		val admin = context.actorSelection("/user/root/admin")
		val uniqueAddress = member.uniqueAddress.address.toString
		val machine = Utils.machineFromPath(uniqueAddress)

		log.info(s"Node that crashed had the following properties: ${machine.toString}")

		authenticator.ask(GetAllRuntimes()).asInstanceOf[Future[List[Runtime]]].map(list => {
			val filtered: List[Runtime] = list.filter(r => {
				val machine = Utils.machineFromPath(r.adminPath)

				// Find all runtimes which were running on this node
				member.address.host.isDefined &&
				machine.ip == member.address.host.get &&
				member.address.port.isDefined &&
				machine.port == member.address.port.get &&
				// There was no time to change the status if it crashed (1 = running)
				r.status == 1
			})

			val results: List[Future[JObject]] = filtered.map(r => {
				authenticator.ask(GetAuthInfoFromUUID(r.owner))
					.asInstanceOf[Future[Option[AuthInfo]]].flatMap(authInfo => {
					authInfo match {
						case None => Future.successful(JObject())
						case Some(a) => admin.ask(RuntimeAdminActor.CreateRuntime(r.jsonDef, a))
							.asInstanceOf[Future[JObject]]
					}
				})
			})

			Future.sequence(results).map((result: List[JObject]) => {
				log.info(s"There were ${filtered.length} runtimes running on the " +
					s"crashed node: ${filtered.toString}")

				// It is an overall success if all restarts are a success
				val success = result.forall((r: JValue) => {
					(r \ "success").extract[Boolean] == true
				})

				if (!success) {
					log.error("Failed to restart runtime from unreachable member")
				}

				val individualSuccess = result.map((r: JValue) => {
					// The individual results per runtime of the restart operation
					val name = (r \ "name").extract[String]
					val result = (r \ "success").extract[Boolean]
					JField(name, result)
				})

				val answer =
					("action" -> "Restart runtimes") ~
					("success" -> success) ~
					("individualSuccess" -> individualSuccess)

				originalSender ! answer
			})
		})
	}

	/**
	 * Deploy the runtime on the same machine this cluster
	 * distributor is also running on. Useful for test purposes
	 * and single-node setups.
	 */
	def local: distributor = (list) => {
		Future(getLocalMachine())
	}

	/**
	 * Returns the local machine in a list.
	 */
	def getLocalMachine(): List[Machine] = {
		val path = Utils.getFullSelfPath(self)
		List(Utils.machineFromPath(path))
	}

	/**
	 * Run the runtime on the next machine since the previous roundRobin
	 * distribution. This means that if the previous runtime was also
	 * distributed according to the roundRobin method and it was started
	 * on machine 1, start the next runtime on machine 2. If there are
	 * a total of 3 machines, runtimes are started on machine 1, 2, 3,
	 * 1, 2, 3, etc respectively.
	 * @return The selected machine.
	 */
	def roundRobin: distributor = (list) => {
		log.info("Choosing next machine for round-robin distribution mode")

		list.map(machines => {
			previousMachine match {
				case None =>
					if (machines.size > 0) {
						previousMachine = Some(machines(0))
						List(machines(0))
					} else {
						List()
					}
				case Some(m) =>
					if (machines.size > 0) {
						log.info(s"Looking up index of machine $m")
						val previousIndex = machines.indexOf(m)
						val nextIndex = (previousIndex + 1) % machines.size
						log.info(s"Previous index was $previousIndex, next index will be $nextIndex.")
						val newPrevious = machines(nextIndex)
						previousMachine = Some(newPrevious)
						List(newPrevious)
					} else {
						List()
					}
			}
		})
	}

	/**
	 * Run the runtime on the machine with the least number of runtimes
	 * currently already running on it. In the case multiple machines
	 * have the same number of runtimes running (or 0), the machine that
	 * responds the fastest to the query how many runtimes are running on
	 * it is chosen.
	 * @return The selected machine.
	 */
	def leastNrRuntimes: distributor = (list) => {
		// Get a list of all machines and the number of runtimes
		// currently running on them. Input list is ignored here
		clusterMonitor.ask(GetPlatformStatistics()).mapTo[JObject].map(answer => {
			val actors = (answer \ "nrActors").extract[Int]
		})

		Future.successful(List())
	}

	/**
	 * Run the runtime on the machine with the least number of actors
	 * currently running on it. In case multiple machines have the same
	 * number of actors, the one which responds the fastest is chosen.
	 * @return The result of the operation.
	 */
	def leastNrActors: distributor = (list) => {
		// Get a list of all machines and the number of runtimes
		// currently running on them. Input list is ignored here
		clusterMonitor.ask(GetPlatformStatistics()).mapTo[JObject].map(answer => {
			val counters = (answer \ "counters").extract[JObject]
			val printed = pretty(render(answer))
		})

		// Get a list of all machines and the number of actors currently running on them
		Future.successful(List())
	}

	/**
	 * Run the runtime on a random machine.
	 * @return The randomly selected machine.
	 */
	def random: distributor = (list) => {
		list.map(machines => {
			val index = rand.nextInt(machines.size)
			List(machines(index))
		})
	}

	/**
	 * Run the runtime on the least busy machine, according to
	 * some criterium for business.
	 * @return The result of the operation.
	 */
	def leastBusy: distributor = (list) => {
		Future.successful(List())
	}

	/**
	 * Split the runtime and run it on the predefined list
	 * of machines. In case any of the machines is unreachable or
	 * not part of the cluster, that machine is left out.
	 * If the number of machines is larger than the number of
	 * actors in the runtime, the first N machines is taken from the list,
	 * where N is equal to the number of actors in the runtime.
	 * @return
	 */
	def splitPredefined: distributor = (list) => {
		Future.successful(List())
	}

	/**
	 * Split the runtime and run it on the least busy N machines in the cluster.
	 * @return The result of the operation.
	 */
	def splitLeastBusy: distributor = (list) => {
		Future.successful(List())
	}

	/**
	 * Split the runtime and run it on a random N number of machines.
	 * @return The result of the operation.
	 */
	def splitRandom: distributor = (list) => {
		Future.successful(List())
	}

	/**
	 * Split the runtime and run it on the N machines with the least number
	 * of runtimes currently running on it.
	 * @return The result of the operation.
	 */
	def splitLeastNrRuntimes: distributor = (list) => {
		Future.successful(List())
	}

	/**
	 * Split the runtime and run it on the N machines with the least number
	 * of actors currently running on it.
	 * @return The result of the operation.
	 */
	def splitLeastNrActors: distributor = (list) => {
		Future.successful(List())
	}
}