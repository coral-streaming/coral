package io.coral.api

import io.coral.actors.Messages._
import spray.http.HttpHeaders.Location
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.http.{HttpResponse, StatusCodes}
import spray.routing.HttpService
import org.json4s.jackson.JsonMethods._
import org.json4s._
import org.json4s.JsonDSL._
import JsonConversions._

class ApiServiceActor extends Actor with ApiService with ActorLogging {
  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing,
  // timeout handling or alternative handler registration
  def receive = runRoute(serviceRoute)
}

trait ApiService extends HttpService {
  private val Type = "actors"

  implicit def executionContext = actorRefFactory.dispatcher
  implicit val timeout = Timeout(1.seconds)

  def coralActor = actorRefFactory.actorSelection("/user/coral")

  // just a few handy shortcut
  def askActor(a: ActorPath, msg:Any) =  actorRefFactory.actorSelection(a).ask(msg)
  def askActor(a: String, msg:Any)    =  actorRefFactory.actorSelection(a).ask(msg)
  def askActor(a: ActorSelection, msg: Any) = a.ask(msg)

  val serviceRoute = {
    pathEndOrSingleSlash {
      complete("api is running. enjoy")
    } ~
      pathPrefix("api") {
        pathPrefix("actors") {
          pathEnd {
            get {
              requestUri{ baseUri =>
                onSuccess(askActor(coralActor,ListActors()).mapTo[List[Long]]) {
                  actorIds => complete(("data" -> actorIds.map(actorId => Map("id" -> actorId.toString, "type" -> "actors", "links" -> Map("self" -> s"$baseUri/$actorId"))))) }
                }
              }
            } ~
            post {
              entity(as[JObject]) { json =>
                val data = (json \ "data").extractOpt[JObject]
                data match {
                  case None => complete(StatusCodes.BadRequest, error("no data key present"))
                  case Some(jsonDef) => {
                    val id = (jsonDef \ "id").extractOpt[String]
                    val theType = (jsonDef \ "type").extractOpt[String]
                    if (id.isDefined) {
                      complete(StatusCodes.Forbidden, error("Client generated id not allowed"))
                    } else if (theType != Some(Type)) {
                      complete(StatusCodes.BadRequest, error("The type must be actors"))
                    } else {
                      onSuccess(askActor(coralActor, CreateActor(jsonDef)).mapTo[Option[Long]]) {
                        case Some(id) => {
                          onSuccess(askActor(coralActor, GetActorPath(id)).mapTo[Option[ActorPath]]) {
                            case None => complete(StatusCodes.InternalServerError, error("not created"))
                            case Some(ap) => {
                              val result = askActor(ap, Get()).mapTo[JObject]
                              onComplete(result) {
                                case Success(json) => {
                                  requestUri { baseUri =>
                                    respondWithHeader(Location(s"$baseUri/$id")) {
                                      complete(("data" -> (json merge render("id" -> id.toString))))
                                    }
                                  }
                                }
                                case Failure(ex) => complete(StatusCodes.InternalServerError, error(s"An error occurred: ${ex.getMessage}"))
                              }
                            }
                          }
                        }
                        case None => complete(error("not created"))
                      }
                    }
                  }
                }
            } ~
            (delete | head | patch) {
              complete(HttpResponse(StatusCodes.MethodNotAllowed))
            }
          }
        } ~
          pathPrefix("actors" / Segment) {
            segment =>
              try {
                val actorId = segment.toLong
                // find my actor
                onSuccess(askActor(coralActor, GetActorPath(actorId)).mapTo[Option[ActorPath]]) {
                  actorPath => {
                    actorPath match {
                      case None => {
                        complete(StatusCodes.NotFound, error(s"actorId ${actorId} not found"))
                      }
                      case Some(ap) => {
                        pathEnd {
                          patch {
                            entity(as[JObject]) { json =>
                              val data = (json \ "data").extractOpt[JObject]
                              data match {
                                case None => complete(StatusCodes.BadRequest, error("no data key present"))
                                case Some(jsonDef) => {
                                  val id = (jsonDef \ "id").extractOpt[String]
                                  val theType = (jsonDef \ "type").extractOpt[String]
                                  if (!id.isDefined || id != Some(actorId.toString)) {
                                    complete(StatusCodes.Forbidden, error("Id must be given and the same as in the URL"))
                                  } else if (theType != Some(Type)) {
                                    complete(StatusCodes.BadRequest, error("The type must be actors"))
                                  } else {
                                    onSuccess(askActor(ap, UpdateProperties(jsonDef)).mapTo[Boolean]) {
                                      case true => complete(StatusCodes.NoContent)
                                      case _ => complete(error("not created"))
                                    }
                                  }
                                }
                              }
                            }
                          } ~
                            get {
                              val result = askActor(ap, Get()).mapTo[JObject]
                              onComplete(result) {
                                case Success(json) => complete(("data" -> (json merge render("id" -> actorId.toString))))
                                case Failure(ex) => complete(StatusCodes.InternalServerError, error(s"An error occurred: ${ex.getMessage}"))
                              }
                            }
                        } ~
                          pathPrefix("in") {
                            post {
                              entity(as[JObject]) { json =>
                                val result = askActor(ap, Shunt(json)).mapTo[JValue]
                                onComplete(result) {
                                  case Success(value) => complete(value)
                                  case Failure(ex) => complete(StatusCodes.InternalServerError, error(s"An error occurred: ${ex.getMessage}"))
                                }
                              }
                            }
                          }
                      }
                    }
                  }
                }
              } catch {
                case e: NumberFormatException => {
                  complete(StatusCodes.NotFound, error(s"actorId ${segment} not found"))
                }
              }
          }
      }
  }

  private def error(message: String) = {
    "errors" -> List(("detail" -> message))
  }
}