package io.coral.actors.transform

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.actors.Messages.{Emit, Trigger}
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

  implicit val injector = new DefaultModule()
  
  val testProbe = TestProbe()
  val instantiationJson = parse( """{ "type": "httpclient", "params": { "url": "http://google.com" } }""")
    .asInstanceOf[JObject]
  val props: Props = CoralActorFactory.getProps(instantiationJson).get
  val actorRef = TestActorRef[HttpClientActor](props)
  actorRef.underlyingActor.emitTargets += testProbe.ref
  implicit val timeout = Timeout(1.seconds)


  "a HttpClientActor" should {

    "not be triggered by an incorrect JSON constructor" in {
      val triggerJson = parse("{}").asInstanceOf[JObject]

      actorRef ! Trigger(triggerJson)
      testProbe.expectNoMsg()
    }

    "execute a GET request on a given URL" in {
      val triggerJson = parse("""{"url": "http://localhost:8111", "method": "GET"}""").asInstanceOf[JObject]

      actorRef ! Trigger(triggerJson)

      testProbe.expectNoMsg(5.seconds)

      val actual = Await.result(actorRef.ask(Emit()), timeout.duration)

      val correctFormat:Boolean = actual match {
        case JObject(List(("status", JString(_)), ("headers", JString(_)), ("body", JString(_)))) => true
          // Do nothing, success
        case _ => false
      }
      assert(correctFormat)
    }

    "emit nothing when the method is invalid" in {
      val triggerJson = parse("""{"url": "http://localhost:8111", "method": "jeMoeder"}""").asInstanceOf[JObject]

      actorRef ! Trigger(triggerJson)

      testProbe.expectNoMsg(5.seconds)

      val actual = Await.result(actorRef.ask(Emit()), timeout.duration)

      val correctFormat:Boolean = actual match {
        case JNothing => true
        // Do nothing, success
        case _ => false
      }

      assert(correctFormat)
    }
  }
}
