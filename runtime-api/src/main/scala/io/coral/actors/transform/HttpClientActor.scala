package io.coral.actors.transform

// scala
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaz.OptionT

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.render

// coral
import io.coral.actors.CoralActor

// spray client
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse}

object HttpClientActor {
  implicit val formats = org.json4s.DefaultFormats

  def apply(json: JValue): Option[Props] = {
    Some(Props(classOf[HttpClientActor], json))
  }
}

class HttpClientActor(json: JObject) extends CoralActor with ActorLogging {
  def jsonDef = json
  def state = Map.empty
  def timer = notSet
  var answer: HttpResponse = _

  def trigger: (JObject) => OptionT[Future, Unit] = {
    json: JObject =>
      try {
        // from trigger data
        val url: String = (json \ "url").extract[String]
        val methodString = (json \ "method").extract[String]
        val payload: JObject = (json \ "payload").extractOrElse[JObject](JObject())

        val method: RequestBuilder = methodString match {
          case "POST" => Post
          case "GET" => Get
          case "PUT" => Put
          case "DELETE" => Delete
          case _ => throw new IllegalArgumentException("method unknown " + methodString)
        }

        import io.coral.api.JsonConversions._
        val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
        val response: Future[HttpResponse] = pipeline(method(url, payload))

        response onComplete {
          case Success(resp) =>
            answer = resp
            log.info("HTTP " + method + " " + url + " " + payload + " successful.")
          case Failure(error) =>
            log.error("Failure: " + error)
        }
      } catch {
        case e: Exception =>
          answer = null
          log.error(e.getMessage)
      }

      OptionT.some(Future.successful({}))
  }

  def emit = {
    json: JObject =>
      if (answer != null) {
        val result = render(("status" -> answer.status.value)
          ~ ("headers" -> answer.headers.toString)
          ~ ("body" -> answer.entity.asString))
        answer = null
        result
      } else {
        JNothing
      }
  }
}