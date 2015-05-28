package io.coral.api

import java.lang.reflect.InvocationTargetException

import io.coral.actors.Messages._
import shapeless.HNil
import spray.http.HttpHeaders.Location
import spray.http.Uri.Query
import spray.http.MediaTypes.`application/vnd.api+json`
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.http._
import spray.routing._
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

  private def coralActor = actorRefFactory.actorSelection("/user/coral")

  // just a few handy shortcut
  private def askActor(a: ActorPath, msg:Any) =  actorRefFactory.actorSelection(a).ask(msg)
  private def askActor(a: ActorSelection, msg: Any) = a.ask(msg)

  val serviceRoute = {
    pathEndOrSingleSlash {
      complete("api is running. enjoy")
    } ~
    pathPrefix("api") {
      jsonApi {
        pathPrefix("actors") {
          pathEnd {
            getActors ~
            addActor
          }
        } ~
        pathPrefix("actors" / Segment) {
          segment => actor(segment) {
            (actorId: String, ap: ActorPath) =>
              pathEnd {
                partialActorUpdate(actorId, ap) ~
                getActor(actorId, ap)
              } ~
              pathPrefix("in") {
                shuntActor(ap)
              }
          }
        }
      }
    }
  }

  private def getActors: Route = get {
    fields { filteredFields =>
      extract(_.request.uri) { uri =>
        val baseUri = uri.withQuery(Query(None))
        onSuccess(askActor(coralActor, ListActors()).mapTo[List[Long]]) {
          actorIds => complete(("data" -> actorIds.map(actorId => filter(Map("id" -> actorId.toString, "type" -> "actors", "links" -> Map("self" -> s"$baseUri/$actorId")), filteredFields))))
        }
      }
    }
  }

  private def addActor: Route = post {
    clientContent { jsonDef: JObject => {
        val id = (jsonDef \ "id").extractOpt[String]
        if (id.isDefined) {
          complete(StatusCodes.Forbidden, error("Client generated id not allowed"))
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
            case None => complete(StatusCodes.InternalServerError, error("not created"))
          }
        }
      }
    }
  }

  private def partialActorUpdate(actorId: String, ap: ActorPath): Route = patch {
    clientContent { jsonDef: JObject => {
        val id = (jsonDef \ "id").extractOpt[String]
        val theType = (jsonDef \ "type").extractOpt[String]
        if (!id.isDefined || id != Some(actorId)) {
          complete(StatusCodes.Forbidden, error("Id must be given and the same as in the URL"))
        } else {
          onSuccess(askActor(ap, UpdateProperties(jsonDef)).mapTo[Boolean]) {
            case true => complete(StatusCodes.NoContent)
            case _ => complete(error("not created"))
          }
        }
      }
    }
  }

  private def getActor(actorId: String, ap: ActorPath): Route = get {
    fields { filteredFields => {
        val result = askActor(ap, Get()).mapTo[JObject]
        onComplete(result) {
          case Success(json) => {
            complete(("data" -> (filter(json, filteredFields) merge render("id" -> actorId))))
          }
          case Failure(ex) => complete(StatusCodes.InternalServerError, error(s"An error occurred: ${ex.getMessage}"))
        }
      }
    }
  }

  private def shuntActor(ap: ActorPath): Route = post {
    entity(as[JObject]) { json => {
        val result = askActor(ap, Shunt(json)).mapTo[JValue]
        onComplete(result) {
          case Success(value) => complete(value)
          case Failure(ex) => complete(StatusCodes.InternalServerError, error(s"An error occurred: ${ex.getMessage}"))
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

  private def jsonApi(f: Route) = {
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

  private def clientContent(f: JObject => Route) = {
    optionalHeaderValueByName("Content-Type") { contentType => {
        if (contentType != Some(`application/vnd.api+json`.value)) {
          complete(StatusCodes.UnsupportedMediaType, error("Only supported Content-Type is application/vnd.api+json"))
        } else {
          entity(as[JObject]) { json =>
            val data = (json \ "data").extractOpt[JObject]
            data match {
              case None => complete(StatusCodes.BadRequest, error("no data key present"))
              case Some(jsonDef) => {
                val theType = (jsonDef \ "type").extractOpt[String]
                if (theType != Some(Type)) {
                  complete(StatusCodes.Conflict, error("The type must be actors"))
                } else {
                  f(jsonDef)
                }
              }
            }
          }
        }
      }
    }
  }

  private def fields: Directive1[Option[Set[String]]] = {
    parameters(s"""fields[$Type]""".?).flatMap { (fields) =>
      val filteredFields = fields.map(_.split(",").toSet ++ Set("type", "id"))
      provide(filteredFields)
    }
  }

  private def actor(segment: String)(f: (String, ActorPath) => Route) = {
    try {
      val actorId = segment.toLong
      onSuccess(askActor(coralActor, GetActorPath(actorId)).mapTo[Option[ActorPath]]) {
        actorPath => {
          actorPath match {
            case Some(ap) => {
              f(segment, ap)
            }
            case None => {
              complete(StatusCodes.NotFound, error(s"actorId ${actorId} not found"))
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