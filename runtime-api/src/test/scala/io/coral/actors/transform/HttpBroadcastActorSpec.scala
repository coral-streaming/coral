package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
 * Created by us73ut on 3/25/15.
 */
class HttpBroadcastActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("HttpServerActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val testProbe = TestProbe()
  val instantiationJson = parse( """{ "type": "httpserver", "params": { "url": "http://google.com" } }""")
    .asInstanceOf[JObject]
}
