package io.coral.actors

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit, ImplicitSender}
import akka.util.Timeout
import io.coral.actors.Messages.{DeleteAllActors, Delete, ListActors, CreateActor}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import akka.pattern.ask

import scala.util.Success

class TestRuntimeActor(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val ec = system.dispatcher
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
      val id1 = runtime ? CreateActor(json1)

      val json2 = parse(
        """{ "type": "stats", "params":
          |{ "field": "amount"}, "group": { "by": "city" } }"""
          .stripMargin).asInstanceOf[JObject]
      val id2 = runtime ? CreateActor(json2)

      val json3 = parse(
        """{ "type": "zscore", "params": { "by": "city",
          |"field": "amount", "score": 2.0 }}"""
          .stripMargin).asInstanceOf[JObject]
      val id3 = runtime ? CreateActor(json3)

      val json4 = parse(
        """{ "type": "httpclient", "params": {
          |"url": "http://localhost:8000/test" }}"""
          .stripMargin).asInstanceOf[JObject]
      val id4 = runtime ? CreateActor(json4)

      val idFutures = Future.sequence(List(id1, id2, id3, id4) map (_.mapTo[Option[Long]]))
      val ids    = Await.result(idFutures, timeout.duration) filter (_.isDefined) map(_.get)
      val result = Await.result(runtime.ask(ListActors()), timeout.duration)
      assert(result == ids)
    }

    "Delete actors on request" in {
      runtime ! DeleteAllActors()

      val json1 = parse("""{"type": "httpserver" }""").asInstanceOf[JObject]
      val id1 = runtime ? CreateActor(json1)

      val result1 = Await.result(id1.mapTo[Option[Long]], timeout.duration)
      runtime ! Delete(result1.get)

      val result2 = Await.result(runtime.ask(ListActors()), timeout.duration)
      assert(result2 == List())

    }
  }
}