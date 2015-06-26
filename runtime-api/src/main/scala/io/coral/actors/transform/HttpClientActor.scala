package io.coral.actors.transform

// scala

import spray.http.HttpHeaders.RawHeader

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}
import scalaz.OptionT
import scala.concurrent.duration._

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.render
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

class HttpClientActor(json: JObject) extends CoralActor with ActorLogging {
  private val ContentTypeJson = "application/json"
  private val TimeOut = 5.seconds

  val (url, method, headers) = HttpClientActor.getParams(jsonDef).get

  def jsonDef = json
  def state   = Map.empty
  override def timer: Timer = {
    val future = getResponse("").run.map{
      case Some(response) => createJson(response)
      case None => JNothing
    }
    try {
      Await.result(future, TimeOut)
    } catch {
      case e: Exception =>
        log.warning("Exception waiting for response", e)
        JNothing
    }
  }

  var answer: HttpResponse = _

  def trigger: (JObject) => OptionT[Future, Unit] = {
    json: JObject =>
      for {
        payload <- getTriggerInputField[String](json \ "payload", "")
        response <- getResponse(payload)
      } yield {
        answer = response
      }
  }

  def getResponse(payload: String): OptionT[Future, HttpResponse] = {
    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
    val value: Future[Option[HttpResponse]] = pipeline(method(url, payload).withHeaders(headers)).map(Some(_))
    OptionT.optionT(value)
  }

  def emit = {
    json: JObject => createJson(answer)
  }

  def createJson(response: HttpResponse): JValue = {
    if (response != null) {
      val headers = JObject(response.headers.map(header => JField(header.name, header.value)))
      val contentType = (headers \ "Content-Type").extractOpt[String] getOrElse ""
      val json = contentType == ContentTypeJson || contentType.startsWith(ContentTypeJson + ";")
      val body = if (json) parse(response.entity.asString) else JString(response.entity.asString)
      val result = render(
        ("status" -> response.status.value)
          ~ ("headers" -> headers)
          ~ ("body" -> body))
      result
    } else {
      JNothing
    }
  }
}