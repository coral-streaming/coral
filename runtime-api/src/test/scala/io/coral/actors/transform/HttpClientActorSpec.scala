package io.coral.actors.transform

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.api.{DefaultModule, ApiServiceActor}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.can.Http
import scala.concurrent.Await
import scala.concurrent.duration._

class HttpClientActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  //Setup HTTP server for testing the client
  val service = system.actorOf(Props[ApiServiceActor], "api")
  IO(Http) ! Http.Bind(service, "localhost", 8111)


  def this() = this(ActorSystem("HttpClientActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(1.seconds)
  implicit val injector = new DefaultModule(system.settings.config)
  
  val testProbe = TestProbe()
  val instantiationJson = parse(
    s"""{
       | "type": "actors",
       | "subtype": "httpclient",
       | "params": { "url": "http://localhost:8111" }
       | }""".stripMargin).asInstanceOf[JObject]

  val props: Props = CoralActorFactory.getProps(instantiationJson).get
  val actorRef = TestActorRef[HttpClientActor](props)
  actorRef.underlyingActor.emitTargets += testProbe.ref


  "a HttpClientActor" should {

    "not be triggered by an incorrect JSON constructor" in {
      val triggerJson = parse("{}").asInstanceOf[JObject]

      actorRef ! triggerJson
      testProbe.expectNoMsg()
    }
  }

}
