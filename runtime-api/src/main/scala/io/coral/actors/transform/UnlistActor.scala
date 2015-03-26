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
            val array = (json \ field).asInstanceOf[JArray]

            array.arr.foreach(element => {
                actorRefFactory.system.scheduler.schedule(0 millis, 0 millis) {
                    //transmit(emit(element))
                }
            })

            OptionT.some(Future.successful({}))
    }

    def emit = {
        json: JObject =>

    }
}