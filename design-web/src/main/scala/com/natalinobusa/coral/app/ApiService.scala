package com.natalinobusa.coral.app

// the service, actors and paths

import akka.actor.Actor

import spray.util._
import spray.http._
import spray.http.MediaTypes._
import spray.http.HttpHeaders.{`Content-Type`, Location}
import spray.routing.HttpService

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

import spray.httpx.Json4sJacksonSupport
/* Used to mix in Spray's Marshalling Support with json4s */
object JsonConversions extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats: Formats = DefaultFormats
}

import scala.collection.mutable.HashMap

class ApiServiceActor extends Actor with ApiService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing,
  // timeout handling or alternative handler registration
  def receive = runRoute(route)
}

// Routing embedded in the actor
trait ApiService extends HttpService {

  // default logging
  implicit val log = LoggingContext.fromActorRefFactory

  val resources = HashMap.empty[String,JObject]
  var counter   = 0L

  val webappRoute = {
    pathSingleSlash {
      redirect("webapp/", StatusCodes.PermanentRedirect)
    } ~
    pathPrefix("webapp") {
      pathEnd {
        redirect("webapp/", StatusCodes.PermanentRedirect)
      } ~
      pathEndOrSingleSlash {
        getFromResource("webapp/index.html")
      } ~
      getFromResourceDirectory("webapp")
    }
  }

  def apiCollectionRoute(collectionName:String) = {
    pathPrefix(collectionName) {
      pathEnd {
        post {
          import JsonConversions._
          entity(as[JObject]) { json =>

            // update the counter
            counter += 1
            val id = counter.toString

            // store the json resource and decorate it with the id field
            val value = JObject(("id", JString(id))) merge json
            resources += (id -> value)

            // build the response
            respondWithHeader(Location(Uri(s"/api/$collectionName/$id"))) {
              complete(StatusCodes.Created, value)
            }
          }
        } ~
          get {
            import JsonConversions._
            complete(StatusCodes.OK, (collectionName, resources.values))
          } ~
          delete {
            complete({
              resources.clear()
              StatusCodes.OK
            },"")
          } ~
          head {
            complete(StatusCodes.OK, "")
          } ~
          complete(StatusCodes.MethodNotAllowed, "")
      } ~
      pathPrefix(Segment) {
        id =>
        pathEnd {
              put {
                import JsonConversions._
                entity(as[JObject]) {
                  json =>

                    // store the json resource and decorate it with the id field
                    val value = JObject(("id", JString(id))) merge json
                    resources += (id -> value)

                    complete(StatusCodes.OK, "")
                }
              } ~
                delete {
                  complete( {
                    resources -= id
                    StatusCodes.OK
                  }, "")
                } ~
                get {
                  import JsonConversions._
                  resources.get(id) match {
                    case Some(json) => complete(StatusCodes.OK, json)
                    case _ => complete(StatusCodes.NotFound, "")
                  }
                } ~
                head {
                  complete(
                    resources.get(id) match {
                      case Some(_) => StatusCodes.OK
                      case _ => StatusCodes.NotFound
                    } , ""
                  )
                } ~
                complete(StatusCodes.MethodNotAllowed, "")
            } ~
        complete(StatusCodes.NotFound, "")
      }
    }
  }

  val apiRoute = {
    pathPrefix("api") {
      apiCollectionRoute("tasks")
    }
  }

  val route = webappRoute ~ apiRoute
}
