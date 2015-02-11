package io.coral.api

import io.coral.actors.Messages._
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.http.{HttpResponse, StatusCodes}
import spray.routing.HttpService
import org.json4s.JObject

class ApiServiceActor extends Actor with ApiService with ActorLogging {
  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing,
  // timeout handling or alternative handler registration
  def receive = runRoute(serviceRoute)
}

// terminology:
// in order not to clash which akka actors,
// we call the REST exposed actors (Beads internal name, external name Actor)
// we call the REST exposed api actor factory as coral, external name coral)
// we call the REST exposed actors connections "connections", exposed as "connections"
// declaration time : /api/coral/flows/{flowid}/actors/{actorid}
trait ApiService extends HttpService {
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
              import JsonConversions._
              ctx => askActor(coralActor,List).mapTo[List[Long]]
                .onSuccess { case actors => ctx.complete(actors)}
            } ~
              post {
                import JsonConversions._
                entity(as[JObject]) { json =>
                  ctx => askActor(coralActor, CreateActor(json)).mapTo[Option[Long]]
                    .onSuccess {
                    case Some(id) => ctx.complete(id.toString)
                    case _ => ctx.complete("not created")
                  }
                }
              } ~
              (delete | head | patch) {
                complete(HttpResponse(StatusCodes.MethodNotAllowed))
              }
          }
        } ~
          pathPrefix("actors" / LongNumber) {
            actorId =>
              // find my actor
              onSuccess(askActor(coralActor, GetActorPath(actorId)).mapTo[Option[ActorPath]]) {
                actorPath => validate(actorPath.isDefined, "") {
                  provide(actorPath.orNull) {
                    ap => {
                      pathEnd {
                        put {
                          import JsonConversions._
                          entity(as[JObject]) { json =>
                            ctx => askActor(ap, UpdateProperties(json)).mapTo[Boolean]
                              .onSuccess {
                              case true => ctx.complete(StatusCodes.Created, "ok")
                              case _ => ctx.complete("not created")
                            }
                          }
                        } ~
                          get {
                            import JsonConversions._
                            val result = askActor(ap, Get()).mapTo[JObject]
                            onComplete(result) {
                              case Success(json) => complete(json)
                              case Failure(ex)   => complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
                            }
                          }
                      } ~
                        pathPrefix("in" ) {
                          post {
                            import JsonConversions._
                            entity(as[JObject]) { json =>
                              val result = askActor(ap, Request(json)).mapTo[JObject]
                              onComplete(result) {
                                case Success(json) => complete(json)
                                case Failure(ex)   => complete(StatusCodes.InternalServerError, s"An error occurred: ${ex.getMessage}")
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
  }
}