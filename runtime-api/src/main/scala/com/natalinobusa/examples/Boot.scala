// Natalino Busa
// http://www.linkedin.com/in/natalinobusa

package com.natalinobusa.examples

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

object Boot extends App {
  implicit val system = ActorSystem()

  // create the coral actor
  val coral = system.actorOf(Props[RuntimeActor], "coral")

  // create and start our service actor
  val service = system.actorOf(Props[ApiServiceActor], "api")

  // fetch configuration from resources/application.config
  val interface = system.settings.config getString "service.interface"
  val port      = system.settings.config getInt    "service.port"

  // start a new HTTP server with our service actor as the handler
  IO(Http) ! Http.Bind(service, interface, port)
}

