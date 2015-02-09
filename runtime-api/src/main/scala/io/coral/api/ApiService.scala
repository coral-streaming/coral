package io.coral.api

import akka.actor.Actor
import org.json4s._
import spray.http.HttpHeaders.Location
import spray.http._
import spray.routing.HttpService
import spray.util._
import scala.collection.mutable.{Map => mMap}
import io.coral.api.JsonConversions._

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
	implicit val log = LoggingContext.fromActorRefFactory
	val resources = mMap.empty[String, JObject]
	val webappRoute = {
		pathSingleSlash {
			redirect("webapp/", StatusCodes.PermanentRedirect)
		} ~ pathPrefix("webapp") {
				pathEnd {
					redirect("webapp/", StatusCodes.PermanentRedirect)
				} ~ pathEndOrSingleSlash {
						getFromResource("webapp/index.html")
					} ~ getFromResourceDirectory("webapp")
			}
	}

	val apiRoute = {
		pathPrefix("api") {
			apiCollectionRoute("tasks")
		}
	}

	val route = webappRoute ~ apiRoute
	var counter = 0L

	def apiCollectionRoute(collectionName: String) = {
		pathPrefix(collectionName) {
			pathEnd {
				post {
					import io.coral.api.JsonConversions._
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
				} ~ get {
						complete(StatusCodes.OK, (collectionName, resources.values))
					} ~ delete {
						complete({
							resources.clear()
							StatusCodes.OK
						}, "")
					} ~ head {
						complete(StatusCodes.OK, "")
					} ~ complete(StatusCodes.MethodNotAllowed, "")
			} ~ pathPrefix(Segment) {
					id =>
						pathEnd {
							put {
								import io.coral.api.JsonConversions._
								entity(as[JObject]) {
									json =>
										// store the json resource and decorate it with the id field
										val value = JObject(("id", JString(id))) merge json
										resources += (id -> value)

										complete(StatusCodes.OK, "")
								}
							} ~ delete {
									complete({
										resources -= id
										StatusCodes.OK
									}, "")
								} ~ get {
									resources.get(id) match {
										case Some(json) => complete(StatusCodes.OK, json)
										case _ => complete(StatusCodes.NotFound, "")
									}
								} ~ head {
									complete(
										resources.get(id) match {
											case Some(_) => StatusCodes.OK
											case _ => StatusCodes.NotFound
										}, ""
									)
								} ~ complete(StatusCodes.MethodNotAllowed, "")
						} ~ complete(StatusCodes.NotFound, "")
				}
		}
	}
}