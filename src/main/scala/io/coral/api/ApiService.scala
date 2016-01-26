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

package io.coral.api

import java.util.UUID
import akka.event.slf4j.Logger
import io.coral.actors.CoralActor.Shunt
import io.coral.actors.RootActor.CreateMachineAdmin
import io.coral.actors.RuntimeAdminActor
import io.coral.actors.RuntimeAdminActor._
import io.coral.api.security.Authenticator.{InvalidationFailed, InvalidationComplete, InvalidationResult}
import io.coral.api.security._
import io.coral.cluster.ClusterDistributor.InvalidateAllAuthenticators
import io.coral.cluster.MachineAdmin._
import io.coral.cluster._
import io.coral.utils.Utils
import spray.http.MediaTypes.`application/json`
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.http._
import spray.routing._
import JsonConversions._
import org.json4s._
import io.coral.api.ApiErrors._

class ApiServiceActor() extends Actor with ApiService with ActorLogging {
  def actorRefFactory = context
  def receive = runRoute(serviceRoute)
}

trait ApiService extends HttpService {
  val authenticatorChooser = new AuthenticatorChooser()
  implicit def executionContext = actorRefFactory.dispatcher
  implicit val timeout = Timeout(10.seconds)
  private def admin = actorRefFactory.actorSelection("/user/root/admin")
  private def cassandra = actorRefFactory.actorSelection("/user/root/cassandra")
  private def authenticatorActor = actorRefFactory.actorSelection("/user/root/authenticator")
  private def clusterMonitor = actorRefFactory.actorSelection("/user/root/clusterMonitor")
  private def askActor(a: ActorPath, msg:Any) = actorRefFactory.actorSelection(a).ask(msg)
  private def askActor(a: ActorSelection, msg: Any) = a.ask(msg)

  /**
   *  API overview:
   *
   *  verb  	URL						called method		description
   *  ----------------------------------------------------------------------------
   *  POST		/platform/cluster   				joinOrLeaveCluster	join a node to the cluster
   *  GET		/platform/cluster					getMachines			get info on machines in the cluster
   *  GET		/platform/cluster/id				getMachine         	get info on a machine in the cluster
   *  GET		/platform/stats						getPlatformStats	show statistics for the entire platform
   *  GET 		/platform/settings					getSettings			returns all settings on the platform
   *  PATCH		/platform/settings					updateSettings		update platform settings
   *  GET		/runtimes							getAllRuntimes		get info on all runtimes on the platform
   *  POST		/runtimes							createRuntime		create a new runtime
   *  DELETE	/runtimes							deleteAllRuntimes	delete all runtimes from the platform
   *  GET		/runtimes/id						getRuntimeInfo		get runtime info
   *  PATCH		/runtimes/id						updateRuntimeSettings starts and stops a runtime
   *  DELETE	/runtimes/id						deleteRuntime		deletes a runtime
   *  GET		/runtimes/id/actors 				getActors			get all actors in a runtime
   *  GET		/runtimes/id/links  				getLinks			get all links in a runtime
   *  GET		/runtimes/id/stats					getRuntimeStats		get runtime statistics of a runtiem
   *  POST		/runtimes/id/actors/id  			postJson			post data to actor but do not return result
   *  POST		/runtimes/id/actors/id/shunt 		shuntActor			post data to actor and return result
   *  GET 		/runtimes/id/actors/id				getActor			get actor info
   *  GET		/runtimes/actors/id/stats 			getActorStats		get actor statistics
   *  GET		/platform/users						getUsers			get all users on the platform
   *  POST		/platform/users						createUser			create a new user on the platform
   *  GET		/platform/users/id					getUser				get user information for a user
   *  DELETE	/platform/users/id					deleteUser			delete a user from the platform
   *  POST		/runtimes/id/permissions 			addPermission		add a new permission
   *  GET		/runtimes/id/permissions/id 		getPermission		get permission info
   *  PATCH		/runtimes/id/permissions/id 		changePermission	change a permission
   *  DELETE	/runtimes/id/permissions/id 		deletePermission	delete a permission
   *  POST		/projects							createProject		create a new project
   *  GET		/projects							getProjects			get all projects on the platform
   *  DELETE	/projects/id						deleteProject		delete a project from the platform
   *  PATCH		/projects/id						updateProject		updates a project
   */
  def serviceRoute = {
    authenticateUser { implicit authInfo =>
      pathEndOrSingleSlash {
        complete("api is running. enjoy")
      } ~ pathPrefix("api") {
        jsonTypes {
		  platform() ~
		  runtimes() ~
		  actors() ~
		  users() ~
		  permissions() ~
		  projects()
		}
	  }
    }
  }

  /**======= AUTHENTICATION METHODS ======**/
  /**=====================================**/

  /**
   * Authenticate a user based on HTTPS + Basic Auth credentials
   * @param f The function to execute when the authentication has succeeded.
   */
  def authenticateUser(f: (AuthInfo) => Route): Route = {
    AuthenticatorChooser.config match {
	  case None =>
	    // Always reject if no authentication mechanism set
	    complete(StatusCodes.Unauthorized)
	  case Some(c) if c == null =>
	    // Not properly set up
	    complete(StatusCodes.Unauthorized)
	  case Some(c) if c.coral.authentication.mode == null =>
	    // Not properly set up
	    complete(StatusCodes.Unauthorized)
	  case Some(c) if c.coral.authentication.mode == "deny-all" =>
	    // Always reject if deny-all
	    complete(StatusCodes.Unauthorized)
      case Some(c) =>
	    authenticate(authenticatorChooser.authenticator(cassandra, authenticatorActor))(f)
    }
  }

  /**
   * Authorize a user based on the rules as stored in the Coral database.
   */
  def authorizeUser(request: HttpRequest, authInfo: AuthInfo, runtime: Option[Runtime])(f: => Route): Route = {
    AuthenticatorChooser.config match {
	  case None =>
		complete(StatusCodes.Unauthorized)
	  case Some(c) =>
	    runtime match {
		  case None =>
		    authorize(authenticatorChooser.coralAuthorizer(request, authInfo))(f)
		  case Some(r) =>
		    authorize(authenticatorChooser.coralAuthorizer(request, authInfo, r))(f)
		}
	}
  }

  /**
   * Authorize a user without a runtime. Shortcut for the method above with runtime = None.
   */
  def authorizeUser(request: HttpRequest, authInfo: AuthInfo)(f: => Route): Route = {
    authorizeUser(request, authInfo, None)(f)
  }

  /**
   * Authorize a user with a given runtime. Shortcut for the method above with runtime = Some(runtime).
   */
  def authorizeUser(request: HttpRequest, authInfo: AuthInfo, runtime: Runtime)(f: => Route): Route = {
	authorizeUser(request, authInfo, Some(runtime))(f)
  }

  /**========== PLATFORM METHODS =========**/
  /**=====================================**/

  /**
   * Platform calls concern all platform and cluster-level interactions that
   * deal with Coral as a whole and not the individual runtimes.
   * You need to have special administrator privileges to change and
   * look up platform settings.
   */
  def platform()(implicit authInfo: AuthInfo) = {
    pathPrefix("platform") {
      pathPrefix("cluster") {
        pathEnd {
          joinOrLeaveCluster() ~
		  getMachines()
        }
      } ~ pathPrefix("cluster" / Segment) { name =>
		machine(name) { machine =>
          pathEnd {
            getMachine(machine)
          }
	    }
	  } ~ pathPrefix("stats") {
        pathEnd {
          getPlatformStats()
        }
      } ~ pathPrefix("settings") {
	    pathEnd {
		  getSettings() ~
		  updateSettings()
	    }
      }
    }
  }

  /**
   * Join a node to the cluster or remove a node from the cluster.
   */
  private def joinOrLeaveCluster()(implicit authInfo: AuthInfo): Route = post {
    extract(_.request) { request =>
      authorize(authenticatorChooser.coralAuthorizer(request, authInfo)) {
        entity(as[JObject]) { json =>
          val machineAdmin = actorRefFactory.actorSelection("/user/root/machineAdmin")
          val joinOrLeave = (json \ "action").extract[String]

          joinOrLeave match {
            case "join" =>
			  // A node that will be joined always runs in stand alone mode.
			  // The machine admin is not running on it yet, so start it.
			  val root = actorRefFactory.actorSelection("/user/root")
			  onSuccess(askActor(root, CreateMachineAdmin()).mapTo[Boolean]) { created =>
			    if (created) {
				  onSuccess(askActor(machineAdmin, JoinCluster(json)).mapTo[JObject]) { answer =>
				    complete(StatusCodes.OK, answer)
				  }
				} else {
				  complete(StatusCodes.InternalServerError)
				}
			  }
            case "leave" =>
              onSuccess(askActor(machineAdmin, LeaveCluster()).mapTo[JObject]) { answer =>
                complete(StatusCodes.OK, answer)
              }
            case other =>
              complete(StatusCodes.BadRequest, invalidJoinLeaveRequest(other))
          }
        }
      }
    }
  }

  /**
   * Get all machines currently in the cluster.
   */
  private def getMachines()(implicit authInfo: AuthInfo): Route = get {
    extract(_.request) { request =>
      authorize(authenticatorChooser.coralAuthorizer(request, authInfo)) {
        val machineAdmin = actorRefFactory.actorSelection("/user/root/machineAdmin")
        onSuccess(askActor(machineAdmin, GetMachines()).mapTo[JObject]) { answer =>
          complete(answer)
        }
      }
    }
  }

  /**
   * Get a machine object from an IP address or alias.
   * @param machineNameOrIp The machine IP ("192.168.0.1") or alias ("machine1").
   */
  private def machine(machineNameOrIp: String)(f: Machine => Route)(implicit authInfo: AuthInfo): Route = {
    time(logToStdOut, "Get machine") {
      // Machine(ip, port, status)
      extract(_.request) { request =>
        authorize(authenticatorChooser.coralAuthorizer(request, authInfo)) {
          val machineAdmin = actorRefFactory.actorSelection("/user/root/machineAdmin")
          onSuccess(askActor(machineAdmin, GetMachine(machineNameOrIp)).mapTo[Option[Machine]]) { answer =>
            answer match {
              case None =>
                complete(StatusCodes.NotFound, machineNotFound(machineNameOrIp))
              case Some(machine) =>
                f(machine)
            }
          }
        }
      }
    }
  }

  /**
   * Returns machine information in JSON format for a given machine.
   */
  private def getMachine(machine: Machine)(implicit authInfo: AuthInfo): Route = get {
    extract(_.request) { request =>
      authorize(authenticatorChooser.coralAuthorizer(request, authInfo)) {
        complete(machine.toJson())
      }
    }
  }

  /**
   * Get platform statistics.
   */
  private def getPlatformStats()(implicit authInfo: AuthInfo): Route = get {
    extract(_.request) { request =>
      authorize(authenticatorChooser.coralAuthorizer(request, authInfo)) {
        onSuccess(askActor(clusterMonitor, ClusterMonitor.GetPlatformStatistics()).mapTo[JObject]) { answer =>
          complete(answer)
        }
      }
    }
  }

  /**
   * Shows current platform settings. Currently not implemented.
   */
  private def getSettings(): Route = get {
    complete("get settings")
  }

  /**
   * Update platform settings. Currently not implemented.
   */
  private def updateSettings(): Route = post {
    complete("update settings")
  }

  /**========== RUNTIME METHODS ==========**/
  /**=====================================**/

  /**
   * Runtime calls deal with individual runtimes on the platform.
   * You need to have permission to access a specific runtime.
   */
  def runtimes()(implicit authInfo: AuthInfo): Route = {
    pathPrefix("runtimes") {
      pathEnd {
        getAllRuntimes() ~
	    createRuntime() ~
	    deleteAllRuntimes()
      }
    } ~ pathPrefix("runtimes" / Segment) { name =>
      runtime(name) { runtime =>
        pathEnd {
          getRuntimeInfo(runtime) ~
		  updateRuntimeSettings(runtime) ~
		  deleteRuntime(runtime)
        } ~ pathPrefix("actors") {
          pathEnd {
            getActors(runtime)
          }
        } ~ pathPrefix("links") {
          pathEnd {
            getLinks(runtime)
          }
        } ~ pathPrefix("stats") {
          pathEnd {
            getRuntimeStats(runtime)
          }
        }
      }
    }
  }

  /**
   * Get information on all runtimes currently running on the platform.
   */
  private def getAllRuntimes()(implicit authInfo: AuthInfo): Route =
    time(logToStdOut, "Get info of all runtimes") {
      get {
        extract(_.request) { request =>
          onSuccess(askActor(authenticatorActor, Authenticator.GetAllRuntimes()).mapTo[List[Runtime]]) { answer =>
            complete(JArray(answer.map(_.toJson)))
          }
        }
      }
    }

  /**
   * Create a new runtime. Asks the runtime admin actor to create a new runtime
   * from the specified JSON.
   */
  private def createRuntime()(implicit authInfo: AuthInfo): Route =
    time(logToStdOut, "Create runtime") {
      post {
        extract(_.request) { request =>
          authorizeUser(request, authInfo) {
            entity(as[JObject]) { json =>
              if (!isOwner(json, authInfo)) {
                complete(StatusCodes.Unauthorized, notLoggedInUser())
              } else {
                onSuccess(askActor(admin, CreateRuntime(json, authInfo)).mapTo[JObject]) { answer =>
				  val success = (answer \ "success").extract[Boolean]
				  success match {
				    case false =>
					  complete(StatusCodes.BadRequest, answer)
				    case true =>
					  val clusterDistributor = actorRefFactory.actorSelection("/user/root/admin/clusterDistributor")
					  onSuccess(askActor(clusterDistributor, InvalidateAllAuthenticators()).mapTo[InvalidationResult]) { result =>
					    result match {
					      case InvalidationComplete() =>
						    complete(StatusCodes.Created, answer)
						  case InvalidationFailed() =>
						    complete(StatusCodes.InternalServerError, json)
					    }
					  }
				    }
				  }
                }
              }
            }
          }
        }
      }

  /**
   * Delete all runtimes currently on the platform.
   * Obviously, take care when executing this method.
   */
  private def deleteAllRuntimes()(implicit authInfo: AuthInfo): Route = delete {
    extract(_.request) { request =>
      authorize(authenticatorChooser.coralAuthorizer(request, authInfo)) {
        onSuccess(askActor(admin, DeleteAllRuntimes()).mapTo[JObject]) { answer =>
          val success = (answer \ "success").extract[Boolean]
          if (success) {
            complete(StatusCodes.OK, answer)
          } else {
            complete(StatusCodes.InternalServerError, answer)
          }
        }
      }
    }
  }

  /**
   * Checks the existence and status of a runtime. Returns a Runtime info
   * object back if the runtime can be found.
   * @param runtimeNameOrUUID The name of the runtime or the UUID of the runtime.
   * @return A Runtime object containing all the runtime information,
   *         or a HTTP 404 error if the runtime with that name cannot
   *         be found.
   */
  private def runtime(runtimeNameOrUUID: String)(f: (Runtime) => Route)(implicit authInfo: AuthInfo): Route =
    time(logToStdOut, "Get runtime") {
	  onSuccess(askActor(authenticatorActor, Authenticator.CheckRuntime(runtimeNameOrUUID, authInfo, cassandra))
	  	.mapTo[Option[Runtime]]) { or => or match {
	  	  case None =>
	  	    complete(StatusCodes.NotFound, runtimeNotFound(runtimeNameOrUUID))
	  	  case Some(r) =>
	  	    f(r)
	    }
	  }
    }

  /**
   * Obtain the runtime information for a given runtime.
   * The runtime information contains the JSON definition, the
   * name, the owner, the project ID, the status, the start time
   * and runtime counters.
   * @param runtime The runtime to get information from.
   */
  private def getRuntimeInfo(runtime: Runtime)(implicit authInfo: AuthInfo): Route =
    time(logToStdOut, "Get runtime info") {
	  get {
        extract(_.request) { request =>
		  authorizeUser(request, authInfo) {
		    complete(runtime.toJson())
          }
        }
      }
	}

  /**
   * Update the settings of a runtime. Includes starting and stopping the runtime.
   */
  private def updateRuntimeSettings(runtime: Runtime)(implicit authInfo: AuthInfo): Route = patch {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
        entity(as[JObject]) { json =>
          onSuccess(askActor(admin, UpdateRuntimeSettings(runtime, json)).mapTo[JObject]) { answer =>
            val success = (answer \ "success").extract[Boolean]
            if (success) {
              complete(StatusCodes.OK, answer)
            } else {
              complete(StatusCodes.InternalServerError, answer)
            }
          }
        }
      }
    }
  }

  /**
   * Delete a runtime from the platform with a given name or UUID.
   */
  private def deleteRuntime(runtime: Runtime)(implicit authInfo: AuthInfo): Route = delete {
    extract(_.request) { request =>
      authorize(authenticatorChooser.coralAuthorizer(request, authInfo)) {
        onSuccess(askActor(admin, DeleteRuntime(runtime)).mapTo[JObject]) { json =>
          val success = (json \ "success").extract[Boolean]
          if (success) {
            complete(StatusCodes.OK, json)
          } else {
            complete(StatusCodes.InternalServerError, json)
          }
        }
      }
    }
  }

  /**
   * Gets an overview of all actors in a runtime
   */
  private def getActors(runtime: Runtime)(implicit authInfo: AuthInfo): Route =
    time(logToStdOut, "Get actors") {
      get {
        extract(_.request) { request =>
          authorizeUser(request, authInfo, runtime) {
            val result = ("actors" -> (runtime.jsonDef \ "actors").asInstanceOf[JArray])
            complete(result)
          }
        }
      }
    }

  /**
   * Get the links section from an actor definition
   */
  private def getLinks(runtime: Runtime)(implicit authInfo: AuthInfo): Route = get {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
        val result = (runtime.jsonDef \ "links").asInstanceOf[JObject]
        complete(result)
      }
    }
  }

  /**
   * Get runtime statistics for a given runtime.
   */
  private def getRuntimeStats(runtime: Runtime)(implicit authInfo: AuthInfo): Route = get {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
        onSuccess(askActor(admin, RuntimeAdminActor.GetRuntimeStatistics(runtime)).mapTo[JObject]) { answer =>
          complete(answer)
        }
      }
    }
  }

  /**=========== ACTOR METHODS ===========**/
  /**=====================================**/

  def actors()(implicit authInfo: AuthInfo): Route = {
    pathPrefix("runtimes" / Segment) { name =>
      runtime(name) { runtime =>
        pathPrefix("actors" / Segment) { name =>
          actor(name, runtime) { path =>
            pathEnd {
              postJson(path, runtime) ~
			  getActor(path, runtime)
            } ~ pathPrefix("shunt") {
              shuntActor(path, runtime)
            } ~ pathPrefix("stats") {
              pathEnd {
                getActorStats(path, runtime)
              }
            }
          }
        }
      }
    }
  }

  /**
   * Gets an actor object from an actor name.
   */
  private def actor(actorName: String, runtime: Runtime)(f: ActorPath => Route)(implicit authInfo: AuthInfo): Route = {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
        try {
          onSuccess(askActor(admin, RuntimeAdminActor.GetActorPath(runtime.uniqueName, actorName))
            .mapTo[Future[Option[ActorPath]]]) { future =>
            onSuccess(future) { answer =>
              answer match {
                case Some(ap) =>
                  f(ap)
                case None =>
                  complete(StatusCodes.NotFound, actorNotFound(actorName))
              }
            }
          }
        } catch {
          case e: NumberFormatException =>
            complete(StatusCodes.NotFound, invalidActorId(actorName))
        }
      }
    }
  }

  /**
   * Post a JSON object to an actor. This does not return the answer to the asker.
   */
  private def postJson(ap: ActorPath, runtime: Runtime)(implicit authInfo: AuthInfo): Route = post {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
        entity(as[JObject]) { json =>
          actorRefFactory.actorSelection(ap) ! json
          complete(StatusCodes.OK)
        }
      }
    }
  }

  /**
   * Gets information from an actor and return it as a JSON object.
   */
  private def getActor(ap: ActorPath, runtime: Runtime)(implicit authInfo: AuthInfo): Route = get {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
        complete("blabla")
      }
    }
  }

  /**
   * Post a JSON object to an actor. This returns the result to the asker and
   * does not send it through the pipeline.
   */
  private def shuntActor(ap: ActorPath, runtime: Runtime)(implicit authInfo: AuthInfo): Route = post {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
        entity(as[JObject]) { json =>
          val result = askActor(ap, Shunt(json)).mapTo[JValue]
          onComplete(result) {
            case Success(value) => complete(value)
            case Failure(ex) => complete(StatusCodes.InternalServerError,
              error(s"An error occurred: ${ex.getMessage}"))
          }
        }
      }
    }
  }

  /**
   * Get statistics from an actor and return it as a JSON object.
   */
  private def getActorStats(actorPath: ActorPath, runtime: Runtime)(implicit authInfo: AuthInfo): Route = get {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
        onSuccess(askActor(admin, RuntimeAdminActor.GetActorStatistics(actorPath, runtime)).mapTo[JObject]) { answer =>
          complete(answer)
        }
      }
    }
  }

  /**============ USER METHODS ===========**/
  /**=====================================**/

  def users()(implicit authInfo: AuthInfo): Route = {
    pathPrefix("users") {
      pathEnd {
        getUsers() ~
		createUser()
      }
    } ~ pathPrefix("users" / Segment) { name =>
      user(name) { user =>
        pathEnd {
          getUser(user) ~
		  deleteUser(user)
        }
      }
    }
  }

  /**
   * Get a user object belonging to a unique user name. Currently not implemented.
   */
  private def user(userName: String)(f: User => Route)(implicit authInfo: AuthInfo): Route = {
    extract(_.request) { request =>
      authorize(authenticatorChooser.coralAuthorizer(request, authInfo)) {
        complete("user")
      }
    }
  }

  /**
   * Get all users currently registered on the platform. Currently not implemented.
   */
  private def getUsers(): Route = get {
    complete("get users")
  }

  /**
   * Create a new user on the platform. Currently not implemented.
   */
  private def createUser(): Route = post {
    complete("create users")
  }

  /**
   * Get user information for a given user. Currently not implemented.
   */
  private def getUser(user: User): Route = get {
    complete("get user")
  }

  /**
   * Delete a user from the platform. Currently not implemented.
   */
  private def deleteUser(user: User): Route = delete {
    complete("delete user")
  }

  /**========= PERMISSION METHODS ========**/
  /**=====================================**/

  def permissions()(implicit authInfo: AuthInfo): Route = {
    pathPrefix("runtimes" / Segment) { name =>
      runtime(name) { runtime =>
        pathPrefix("permissions") {
          pathEnd {
            addPermission(runtime)
          }
        } ~ pathPrefix("permissions" / Segment) { id =>
          pathEnd {
            getPermission(runtime, id) ~
		    changePermission(runtime, id) ~
		    deletePermission(runtime, id)
          }
        }
      }
    }
  }

  /**
   * Adds a permission for a given runtime.
   */
  private def addPermission(runtime: Runtime)(implicit authInfo: AuthInfo): Route = post {
    extract(_.request) { request =>
	  authorizeUser(request, authInfo, runtime) {
	    entity(as[JObject]) { json =>
		  val permissionHandler = actorRefFactory.actorSelection("/user/root/authenticator/permissionHandler")
		  onSuccess(askActor(permissionHandler, AddPermission(json)).mapTo[JObject]) { answer =>
		    complete(StatusCodes.Created, answer)
	      }
	    }
      }
    }
  }

  /**
   * Obtain whether a permission is set to true (allowed) or false (denied).
   */
  private def getPermission(runtime: Runtime, id: String)(implicit authInfo: AuthInfo): Route = get {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
	    val permissionHandler = actorRefFactory.actorSelection("/user/root/authenticator/permissionHandler")
	    onSuccess(askActor(permissionHandler, GetPermission(authInfo.id, UUID.fromString(id))).mapTo[JObject]) { answer =>
		  // If it has a success field, it is not a success
		  val success = (answer \ "success").extractOrElse[Boolean](true)

		  if (success) {
		    val allowed = (answer \ "allowed").extract[Boolean]
		    val result = ("allowed" -> allowed)
		    complete(result)
		  } else {
		    complete(StatusCodes.NotFound, permissionNotFound(id))
		  }
	    }
	  }
    }
  }

  /**
   * Set a permission from allowed to denied or the other way around.
   */
  private def changePermission(runtime: Runtime, id: String)(implicit authInfo: AuthInfo): Route = patch {
    extract(_.request) { request =>
	  authorizeUser(request, authInfo, runtime) {
	    entity(as[JObject]) { json =>
		  val permissionHandler = actorRefFactory.actorSelection("/user/root/authenticator/permissionHandler")
		  val allowed = (json \ "allowed").extract[Boolean]
		  onSuccess(askActor(permissionHandler, UpdatePermission(authInfo.id, UUID.fromString(id), allowed)).mapTo[JObject]) { answer =>
		    complete(answer)
		  }
	    }
	  }
	}
  }

  /**
   * Delete a permission from a runtime.
   */
  private def deletePermission(runtime: Runtime, id: String)(implicit authInfo: AuthInfo): Route = delete {
    extract(_.request) { request =>
      authorizeUser(request, authInfo, runtime) {
	    Utils.tryUUID(id) match {
		  case None => complete(StatusCodes.BadRequest, invalidPermissionId())
		  case Some(p) =>
		    val permissionHandler = actorRefFactory.actorSelection("/user/root/authenticator/permissionHandler")
		    onSuccess(askActor(permissionHandler, RemovePermission(authInfo.id, p)).mapTo[JObject]) { answer =>
			  complete(answer)
		    }
		}
      }
    }
  }

  /**========== PROJECT METHODS ==========**/
  /**=====================================**/

  def projects()(implicit authInfo: AuthInfo): Route = {
    pathPrefix("projects") {
      pathEnd {
		  createProject() ~
		  getProjects()

      }
    } ~ pathPrefix("projects" / Segment) { name =>
      project(name) { project =>
        pathEnd {
          deleteProject(project) ~
		  updateProject(project)
        }
      }
    }
  }

  /**
   * Gets a project object belonging to a project name. Currently not implemented.
   */
  private def project(projectName: String)(f: Project => Route)(implicit authInfo: AuthInfo): Route = {
    extract(_.request) { request =>
	  complete("project")
    }
  }

  /**
   * Get all projects currently on the platform. Currently not implemented.
   */
  private def getProjects(): Route = get {
    complete("get projects")
  }

  /**
   * Update a definition of a project. Currently not implemented.
   */
  private def updateProject(project: Project): Route = put {
    complete("update project")
  }

  /**
   * Create a new project on the platform. Currently not implemented.
   */
  private def createProject(): Route = post {
    complete("create project")
  }

  /**
   * Delete a project from the platform. Currently not implemented.
   */
  private def deleteProject(project: Project): Route = delete {
    complete("delete project")
  }

  /**========== HELPER METHODS ===========**/
  /**=====================================**/

  /**
   * Time a given action and send the result to the console.
   */
  private def time(log: (HttpRequest, HttpResponse, String, Long) => Unit, traceName: String): Directive0 = {
    mapRequestContext { ctx =>
      val timestamp = System.currentTimeMillis
      ctx.withHttpResponseMapped { response =>
        log(ctx.request, response, traceName, System.currentTimeMillis - timestamp)
        response
      }
    }
  }

  /**
   * Log a timing message to standard out.
   */
  private def logToStdOut(request: HttpRequest, response: HttpResponse, traceName: String, time: Long): Unit = {
    val requestString = request.toString.replace("\n", "")
    val responseString = response.toString.replace("\n", "")
    Logger("io.coral.api.ApiService").info(s""""$traceName" took $time ms. """ +
		s"""(Request: $requestString, response: $responseString)""")
  }

  /**
   * Make sure that the headers of the request are set to "application/json".
   */
  private def jsonTypes(f: Route): Route = {
    optionalHeaderValueByName("Accept") { accept =>
	  if (!accept.contains(`application/json`.value)) {
        complete(StatusCodes.NotAcceptable, mimeTypeIncorrect())
      } else {
        respondWithMediaType(`application/json`) {
          f
        }
      }
    }
  }

  /**
   * Checks whether the owner as specified in the JSON runtime
   * definition is the currently logged in owner.
   */
  def isOwner(json: JObject, authInfo: AuthInfo): Boolean = {
    val owner = (json \ "owner").extractOpt[String]

    authInfo.user match {
      case Right(_) =>
	    // Accept all user
	    true
      case Left(user) =>
        owner match {
          // if no owner present, this will later be checked by RuntimeAdminActor
          case None => true
          case Some(o) => {
		    // the owner can be specified as UUID or as unique name
		    val uuid: Option[UUID] = Utils.tryUUID(o)

		    uuid match {
		      case Some(u) => user.id == u
		      case None => user.uniqueName == o
		    }
		  }
        }
    }
  }
}