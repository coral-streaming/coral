package io.coral.api

import akka.actor.ActorSystem
import akka.actor.Props
import akka.io.IO
import akka.util.Timeout
import io.coral.actors.RuntimeActor
import spray.can.Http
import scala.concurrent.duration._
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

import io.coral.lib.Metrics

object Boot extends App {
  implicit val system = ActorSystem()
  
  // create the coral actor
  val coral = system.actorOf(Props(classOf[RuntimeActor], new DefaultModule(system.settings.config)), "coral")

  // start the metrics collecting
  Metrics.startReporter(system)

  // create and start our service actor
  val service = system.actorOf(Props[ApiServiceActor], "api")

  // fetch configuration from resources/application.config
  val interface = system.settings.config getString "service.interface"
  val port = system.settings.config getInt "service.port"

  // start a new HTTP server with our service actor as the handler
  implicit val timeout = Timeout(5.seconds)
  val future = (IO(Http) ? Http.Bind(service, interface, port)).map {
    case _: Http.Bound => true
    case _ => false
  }
  val bounded = Await.result(future, timeout.duration)
  if (!bounded) {
    System.exit(-1)
  }
}