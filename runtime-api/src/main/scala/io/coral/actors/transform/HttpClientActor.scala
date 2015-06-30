package io.coral.actors.transform

// scala

import spray.http.HttpHeaders.RawHeader

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{compact, render}
import org.json4s.native.JsonMethods._

// coral
import io.coral.actors.CoralActor

// spray client
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse}

object HttpClientActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      url <- (json \ "attributes" \ "params" \ "url").extractOpt[String]
      method <- (json \ "attributes" \ "params" \ "method").extractOpt[String].flatMap(createRequestBuilder)
      headers <- Some((json \ "attributes" \ "params" \ "headers").extractOrElse[JObject](JObject())).map(createHeaders)
    } yield(url, method, headers)
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[HttpClientActor], json))
  }

  private def createHeaders(json: JObject): List[RawHeader] = {
    json.values.map{case (key, value) => RawHeader(key, value.asInstanceOf[String])}.toList
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

  val (url, method, headers) = HttpClientActor.getParams(jsonDef).get

  override def timer = {
    getResponse("")
  }

  override def trigger = {
    json: JObject => getResponse(compact(render(json)))
  }

  def getResponse(payload: String): Future[Option[JValue]] = {
    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
    pipeline(method(url, payload).withHeaders(headers)).map(result => Some(createJson(result)))
  }

  def createJson(response: HttpResponse): JValue = {
    val headers = JObject(response.headers.map(header => JField(header.name, header.value)))
    val contentType = (headers \ "Content-Type").extractOpt[String] getOrElse ""
    val json = contentType == ContentTypeJson || contentType.startsWith(ContentTypeJson + ";")
    val body = if (json) parse(response.entity.asString) else JString(response.entity.asString)
    render(
      ("status" -> response.status.value)
        ~ ("headers" -> headers)
        ~ ("body" -> body))
  }
}