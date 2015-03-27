package io.coral.actors.transform

import akka.actor.{Props, ActorLogging}
import io.coral.actors.CoralActor
import org.json4s.JObject
import org.json4s.JsonAST.JValue
import spray.client.pipelining._
import spray.http.{HttpResponse, HttpRequest}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import scala.concurrent.duration._

import scalaz.OptionT

object UnlistActor {
    implicit val formats = org.json4s.DefaultFormats

    def getParams(json: JValue) = {
        for {
            field <- (json \ "params" \ "field").extractOpt[String]
        } yield {
            field
        }
    }

    def apply(json: JValue): Option[Props] = {
        getParams(json).map(_ => Props(classOf[UnlistActor], json))
    }
}

class UnlistActor(json: JObject) extends CoralActor with ActorLogging {
    def jsonDef = json
    def state = Map.empty
    def timer = notSet

    val field = UnlistActor.getParams(json).get

    def trigger = {
        json: JObject =>
            val array = (json \ field)

            array match {
                case JArray(a) =>
                    // The system scheduler cannot be used here because
                    // the order in which objects are sent can then not
                    // be guaranteed.
                    a.arr.foreach(element => {
                        emitTargets.foreach(t => t ! element.asInstanceOf[JObject])
                    })
                case _ =>
                    // Do nothing when not receiving any other object
                    // than an array, including a JNothing
            }

            OptionT.some(Future.successful({}))
    }

    def emit = {
        json: JObject =>
            JNothing
    }
}