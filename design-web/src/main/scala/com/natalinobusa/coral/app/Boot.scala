package com.natalinobusa.coral.app

import akka.actor.{ ActorSystem, Props }
import akka.io.IO
import spray.can.Http

object Boot extends App {
  implicit val system = ActorSystem()

  // create and start our service actor
  val service = system.actorOf(Props[ApiServiceActor], "api")

  // fetch configuration from resources/application.config
  val interface = system.settings.config getString "service.interface"
  val port      = system.settings.config getInt    "service.port"

  // start a new HTTP server with our service actor as the handler
  IO(Http) ! Http.Bind(service, interface, port)
}
