package io.coral.api

import java.lang.reflect.InvocationTargetException

import io.coral.actors.Messages._
import shapeless.HNil
import spray.http.HttpHeaders.Location
import spray.http.HttpHeaders.`Content-Type`
import spray.http.Uri.Query
import spray.routing.directives.HeaderDirectives.optionalHeaderValue
import spray.http.MediaTypes.`application/vnd.api+json`
import spray.httpx.unmarshalling.Unmarshaller
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.http._
import spray.routing.{Directive0, Directive1, Directive, HttpService}
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
        jsonApi {
                pathPrefix("actors") {
                  pathEnd {
                    get {
                      fields { filteredFields =>
                        extract(_.request.uri) { uri =>
                          val baseUri = uri.withQuery(Query(None))
                          onSuccess(askActor(coralActor, ListActors()).mapTo[List[Long]]) {
                            actorIds => complete(("data" -> actorIds.map(actorId => filter(Map("id" -> actorId.toString, "type" -> "actors", "links" -> Map("self" -> s"$baseUri/$actorId")), filteredFields))))
                          }
                        }
                      }
                    }
                  } ~
                    post {
                      clientContentType {
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
                                    complete(StatusCodes.Conflict, error("The type must be actors"))
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
                                    clientContentType {
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

                                    }
                                  } ~
                                    get {
                                      fields { filteredFields =>
                                          val result = askActor(ap, Get()).mapTo[JObject]
                                          onComplete(result) {
                                            case Success(json) => {
                                              complete(("data" -> (filter(json, filteredFields) merge render("id" -> actorId.toString))))
                                            }
                                            case Failure(ex) => complete(StatusCodes.InternalServerError, error(s"An error occurred: ${ex.getMessage}"))
                                          }
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
        }


  private def filter(data: Map[String, _], filter: Option[Set[String]]): Map[String, _] = {
    filter match {
      case None => data
      case Some(filter) => data.filterKeys(filter.contains(_))
    }
  }

  private def filter(data: JObject, filter: Option[Set[String]]): JObject = {
    filter match {
      case None => data
      case Some(filter) => data.filterField{case (field, value) => filter.contains(field)}
    }
  }

  private def error(message: String) = {
    "errors" -> List(("detail" -> message))
  }

  private def jsonApi(f: spray.routing.RequestContext => Unit) = {
    optionalHeaderValueByName("Accept") {
      accept =>
        if (accept != Some(`application/vnd.api+json`.value)) {
          complete(StatusCodes.NotAcceptable, error("specified mime type not supported to be returned by the server"))
        } else {
          respondWithMediaType(`application/vnd.api+json`) {
            parameters("include".?, "sort".?) { (include, sort) =>
              if (include.isDefined) {
                complete(StatusCodes.BadRequest, error("include not supported"))
              } else if (sort.isDefined) {
                complete(StatusCodes.BadRequest, error("sort not supported"))
              } else {
                f
              }
            }
          }
        }
    }
  }

  private def clientContentType: Directive0 = {
    optionalHeaderValueByName("Content-Type").flatMap {
      contentType =>
        if (contentType != Some(`application/vnd.api+json`.value)) {
          complete(StatusCodes.UnsupportedMediaType, error("Only supported Content-Type is application/vnd.api+json"))
        } else {
          pass
        }
    }
  }

  private def fields: Directive1[Option[Set[String]]] = {
    parameters(s"""fields[$Type]""".?).flatMap { (fields) =>
      val filteredFields = fields.map(_.split(",").toSet ++ Set("type", "id"))
      provide(filteredFields)
    }
  }
}