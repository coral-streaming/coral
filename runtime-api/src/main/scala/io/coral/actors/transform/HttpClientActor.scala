package io.coral.actors.transform

//scala
import scala.concurrent.Future
import scala.util.{Failure, Success}

// coral
import io.coral.actors.CoralActor
import org.json4s._

//spray client
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse}

object HttpClientActor {
  //akka actors props
  import akka.actor.Props

  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
    // from json actor definition
    // possible parameters server/client, url, etc
      url <- (json \ "params" \ "url").extractOpt[String]
    } yield {
      (url)
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[HttpClientActor], json))
    // todo: take better care of exceptions and error handling
  }
}

class HttpClientActor(json: JObject) extends CoralActor {
  def jsonDef = json
  val (url) = HttpClientActor.getParams(jsonDef).get
  def state = Map.empty

  def timer = notSet

  def trigger = {
    json: JObject =>
      for {
      // from trigger data
        outlier <- getTriggerInputField[Boolean](json \ "outlier")
      } yield {
        // post
        import io.coral.api.JsonConversions._
        val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
        val response = pipeline(Post(url, json))

        response onComplete {
          case Success(resp) => log.warning("successfully posted")
          case Failure(error) => log.warning("Failure: " + error)
        }
      }
  }

  def emit = doNotEmit
}