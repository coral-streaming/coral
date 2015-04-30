package io.coral.actors.transform

import akka.actor.{ActorRefFactory, Actor, ActorSystem, Props}
import akka.io.IO
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import akka.pattern.ask
import io.coral.actors.CoralActorFactory
import io.coral.api.{JsonConversions, DefaultModule}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import spray.can.Http
import spray.routing.HttpService
import scala.concurrent.Await
import scala.concurrent.duration._

class HttpClientActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout = Timeout(2.seconds)

  //Setup HTTP server for testing the client
  val service = system.actorOf(Props[HttpTestActor])
  IO(Http) ? Http.Bind(service, "localhost", 8111)

  override def afterAll() {
    Http.Unbind
    TestKit.shutdownActorSystem(system)
  }

  implicit val injector = new DefaultModule(system.settings.config)
  implicit val formats = org.json4s.DefaultFormats

  def this() = this(ActorSystem("HttpClientActorSpec"))
  
  val testProbe = TestProbe()

  "a HttpClientActor" should {

    "not create a new actor with an incorrect JSON definition" in {
      val instantiationJson = parse(
        s"""{
           | "type": "httpclient",
           | "params": { "url": "http://localhost:8111" }
           | }""".stripMargin).asInstanceOf[JObject]

      val httpClientActor = HttpClientActor(instantiationJson)
      assert(httpClientActor == None)
    }

    "emit the retrieved content" in {
      val instantiationJson = parse(
        s"""{
           | "type": "httpclient",
           | "params": { "url": "http://localhost:8111/text", "method": "GET" }
           | }""".stripMargin).asInstanceOf[JObject]
      val props: Props = CoralActorFactory.getProps(instantiationJson).get
      val actorRef = TestActorRef[HttpClientActor](props)
      actorRef.underlyingActor.emitTargets += testProbe.ref

      val triggerJson = parse("{}").asInstanceOf[JObject]

      actorRef ! triggerJson

      val json = testProbe.receiveOne(1.seconds).asInstanceOf[JObject]

      assert((json \ "status").extract[String] == "200 OK")
      assert((json \ "headers" \ "Content-Type").extract[String] == "text/plain; charset=UTF-8")
      assert((json \ "body").extract[String] == "content")
    }

    "emit json content as json" in {
      val instantiationJson = parse(
        s"""{
           | "type": "httpclient",
           | "params": { "url": "http://localhost:8111/json", "method": "GET" }
           | }""".stripMargin).asInstanceOf[JObject]
      val props: Props = CoralActorFactory.getProps(instantiationJson).get
      val actorRef = TestActorRef[HttpClientActor](props)
      actorRef.underlyingActor.emitTargets += testProbe.ref
      val triggerJson = parse("{}").asInstanceOf[JObject]

      actorRef ! triggerJson

      val json = testProbe.receiveOne(1.seconds).asInstanceOf[JObject]

      assert((json \ "body" \ "content").extract[String] == "jsoncontent")
    }

    "send header to the server" in {
      val instantiationJson = parse(
        s"""{
           | "type": "httpclient",
           | "params": { "url": "http://localhost:8111/header", "method": "GET", "headers": { "Authorization": "mykey" } }
           | }""".stripMargin).asInstanceOf[JObject]
      val props: Props = CoralActorFactory.getProps(instantiationJson).get
      val actorRef = TestActorRef[HttpClientActor](props)
      actorRef.underlyingActor.emitTargets += testProbe.ref
      val triggerJson = parse("{}").asInstanceOf[JObject]

      actorRef ! triggerJson

      val json = testProbe.receiveOne(1.seconds).asInstanceOf[JObject]

      assert((json \ "body").extract[String] == "The authorization received is mykey")
    }

    "send payload to the server " in {
      val instantiationJson = parse(
        s"""{
           | "type": "httpclient",
           | "params": { "url": "http://localhost:8111/text", "method": "POST" }
           | }""".stripMargin).asInstanceOf[JObject]
      val props: Props = CoralActorFactory.getProps(instantiationJson).get
      val actorRef = TestActorRef[HttpClientActor](props)
      actorRef.underlyingActor.emitTargets += testProbe.ref

      val triggerJson = parse("""{"payload": "mypayload"}""").asInstanceOf[JObject]

      actorRef ! triggerJson

      val json = testProbe.receiveOne(1.seconds).asInstanceOf[JObject]

      assert((json \ "body").extract[String] == "The received payload is mypayload")
    }
  }
}

class HttpTestActor extends Actor with HttpService {

  override def receive: Receive = runRoute(serviceRoute)

  override implicit def actorRefFactory: ActorRefFactory = context

  implicit val system = context.system

  val serviceRoute = {
    pathPrefix("text") {
      pathEnd {
        get {
          complete("content")
        } ~ post {
          entity(as[String]) { payload =>
            complete(s"The received payload is $payload")
          }
        }
      }
    } ~ pathPrefix("json") {
      pathEnd {
        get {
          import JsonConversions._
          complete(parse( """{"content": "jsoncontent"}"""))
        }
      }
    } ~ pathPrefix("header") {
      pathEnd {
        get {
          headerValueByName("Authorization") { authorization =>
            complete(s"The authorization received is $authorization")
          }
        }
      }
    }
  }
}

