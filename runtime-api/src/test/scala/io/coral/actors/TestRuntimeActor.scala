package io.coral.actors

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit, ImplicitSender}
import akka.util.Timeout
import io.coral.actors.Messages.{DeleteAllActors, Delete, ListActors, CreateActor}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

import scala.util.Success

class TestRuntimeActor(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout = Timeout(100.millis)
  def this() = this(ActorSystem("MySpec"))
  val runtime = system.actorOf(Props[RuntimeActor], "coral")

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A RuntimeActor" must {
    "Create actors on request" in {
      runtime ! DeleteAllActors()

      val json1 = parse("""{"type": "httpserver" }""").asInstanceOf[JObject]
      runtime ! CreateActor(json1)
      expectMsg(Some(1))

      val json2 = parse(
        """{ "type": "stats", "params":
          |{ "field": "amount"}, "group": { "by": "city" } }"""
          .stripMargin).asInstanceOf[JObject]
      runtime ! CreateActor(json2)
      expectMsg(Some(2))

      val json3 = parse(
        """{ "type": "zscore", "params": { "by": "city",
          |"field": "amount", "score": 2.0 }}"""
          .stripMargin).asInstanceOf[JObject]
      runtime ! CreateActor(json3)
      expectMsg(Some(3))

      val json4 = parse(
        """{ "type": "httpclient", "params": {
          |"url": "http://localhost:8000/test" }}"""
          .stripMargin).asInstanceOf[JObject]
      runtime ! CreateActor(json4)
      expectMsg(Some(4))

      val result = Await.result(runtime.ask(ListActors()), timeout.duration)
      assert(result == List(1, 2, 3, 4))
    }

    "Delete actors on request" in {
      runtime ! DeleteAllActors()

      val result1 = Await.result(runtime.ask(ListActors()), timeout.duration)
      assert(result1 == List())

      val json1 = parse("""{"type": "httpserver" }""").asInstanceOf[JObject]
      runtime ! CreateActor(json1)
      expectMsg(Some(1))

      runtime ! Delete(1)

      val result2 = Await.result(runtime.ask(ListActors()), timeout.duration)
      assert(result2 == List())

      val json2 = parse("""{"type": "httpserver" }""").asInstanceOf[JObject]
      runtime ! CreateActor(json2)
      // We do not expect that the counter starts over again or fills in the gaps
      expectMsg(Some(2))

      runtime ! Delete(2)

      val result3 = Await.result(runtime.ask(ListActors()), timeout.duration)
      assert(result3 == List())
    }
  }
}