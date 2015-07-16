package io.coral.actors.connector

import java.io.FileWriter
import akka.actor.Props
import io.coral.actors.CoralActor
import org.json4s.JsonAST.JObject
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.concurrent.Future

/**
 * This log actor is meant for testing a defined pipeline, not for production use. The intended production use is to write to Kafka using the Kafka consumer.
 */
object LogActor {

  implicit val formats = org.json4s.DefaultFormats


  def getParams(json: JValue) = {
    for {
      file <- (json \ "attributes" \ "params" \ "file").extractOpt[String]
    } yield {
      val append = (json \ "attributes" \ "params" \ "append").extractOpt[Boolean]
      (file, append getOrElse false)
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[LogActor], json))
  }
}

class LogActor(json: JObject)
  extends CoralActor(json) {

  val (file, append) = LogActor.getParams(json).get

  var fileWriter: FileWriter = _

  override def preStart() = {
    fileWriter = new FileWriter(file, append)
  }

  override def postStop() = {
    fileWriter.close
  }

  override def trigger = {
    json =>
      Future {
        fileWriter.write(compact(json) + System.lineSeparator)
        fileWriter.flush
        Some(JNothing)
      }
  }

}