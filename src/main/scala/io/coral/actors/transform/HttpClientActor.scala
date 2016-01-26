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

package io.coral.actors.transform

import spray.http.HttpHeaders.RawHeader
import scala.concurrent.Future
import akka.actor.{ActorLogging, Props}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{compact, render}
import org.json4s.native.JsonMethods._
import io.coral.actors.CoralActor
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse}

/**
 * Simple HTTP client actor to handle HTTP requests.
 * Can handle POST, GET, PUT and DELETE requests.
 *
 * The "mode" parameter specifies how the URL to which the HTTP
 * request should be submitted is obtained:
 * - "static": an "url" field is present in the constructor which
 *   specifies what the endpoint of the request is.
 * - "dynamic": a "field" field is present in the constructor which
 *   specifies what the name of the field is which contains the URL
 *   to submit the request to.
 *
 * The "response" parameter specifies what should be done with
 * the result of a succesful HTTP request. It can be one of two:
 * - "emit": emit the result to any receiver specified in
 *   the runtime definition.
 * - "none": save the result in the variable "latestResponse".
 *   return it when the state of this actor is collected.
 */
object HttpClientActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		for {
			mode <- (json \ "params" \ "mode").extractOpt[String]
			response <- (json \ "params" \ "response").extractOpt[String]
			method <- (json \ "params" \ "method").extractOpt[String].flatMap(createRequestBuilder)
			headers <- Some((json \"params" \ "headers").extractOrElse[JObject](JObject())).map(createHeaders)
		} yield {
			val url = (json \ "params" \ "url").extractOpt[String]
			val field = (json \ "params" \ "field").extractOpt[String]
			(mode, response, field, url, method, headers)
		}
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[HttpClientActor], json))
	}

	private def createHeaders(json: JObject): List[RawHeader] = {
		json.values.map { case (key, value) => RawHeader(key, value.asInstanceOf[String]) }.toList
	}

	private def createRequestBuilder(method: String): Option[RequestBuilder] = {
		method match {
			case "POST" => Some(Post)
			case "GET" => Some(Get)
			case "PUT" => Some(Put)
			case "DELETE" => Some(Delete)
			case _ => {
				None
			}
		}
	}
}

class HttpClientActor(json: JObject) extends CoralActor(json) with ActorLogging {
	private val ContentTypeJson = "application/json"
	val (mode, response, field, url, method, headers) = HttpClientActor.getParams(jsonDef).get
	var latestResponse: Future[Option[JValue]] = _

	override def trigger = {
		json: JObject => {
			val answer: Future[Option[JValue]] = mode match {
				case "static" =>
					// The URL should be supplied with the constructor
					if (url.isDefined) {
						getResponse(compact(render(json)), url.get)
					} else {
						Future.successful(None)
					}
				case "dynamic" =>
					// The URL is supplied in the trigger JSON
					if (field.isDefined) {
						val givenUrl = (json \ field.get).extract[String]
						getResponse(compact(render(json)), givenUrl)
					} else {
						Future.successful(None)
					}
				case _ =>
					throw new Exception("Cannot handle other mode than 'static' or 'dynamic'.")
			}

			response match {
				case "emit" =>
					answer
				case "none" =>
					latestResponse = answer
					// This will not emit the result
					Future.successful(None)
				case _ =>
					throw new Exception("Cannot handle other response than 'emit' or 'none'.")
			}
		}
	}

	def getResponse(payload: String, url: String): Future[Option[JValue]] = {
		val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
		pipeline(method(url, payload).withHeaders(headers)).map(result => Some(createJson(result)))
	}

	def createJson(response: HttpResponse): JValue = {
		val headers = JObject(response.headers.map(header => JField(header.name, header.value)))
		val contentType = (headers \ "Content-Type").extractOpt[String] getOrElse ""
		val json = contentType == ContentTypeJson || contentType.startsWith(ContentTypeJson + ";")

		val body = if (json) {
			parse(response.entity.asString)
		} else {
			JString(response.entity.asString)
		}

		val result =
			("status" -> response.status.value) ~
			("headers" -> headers) ~
			("body" -> body)

		render(result)
	}
}