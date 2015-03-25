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

class TestRuntimeActor(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))
  val runtime = system.actorOf(Props[RuntimeActor], "coral")

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A RuntimeActor" must {
    "Create actors on request" in {
      runtime ! DeleteAllActors()
      expectNoMsg()

      val json1 = parse("""{"type": "httpserver" }""").asInstanceOf[JObject]
      runtime ! CreateActor(json1)
      val id1 = receiveOne(500.millisecond).asInstanceOf[Option[Long]]

      val json2 = parse(
        """{ "type": "stats", "params":
          |{ "field": "amount"}, "group": { "by": "city" } }"""
          .stripMargin).asInstanceOf[JObject]
      runtime ! CreateActor(json2)
      val id2 = receiveOne(500.millisecond).asInstanceOf[Option[Long]]

      val json3 = parse(
        """{ "type": "zscore", "params": { "by": "city",
          |"field": "amount", "score": 2.0 }}"""
          .stripMargin).asInstanceOf[JObject]
      runtime ! CreateActor(json3)
      val id3 = receiveOne(500.millisecond).asInstanceOf[Option[Long]]

      val json4 = parse(
        """{ "type": "httpclient", "params": {
          |"url": "http://localhost:8000/test" }}"""
          .stripMargin).asInstanceOf[JObject]
      runtime ! CreateActor(json4)
      val id4 = receiveOne(500.millisecond).asInstanceOf[Option[Long]]

      val ids = List(id1, id2, id3, id4) map (_.getOrElse(-1))
      runtime ! ListActors()
      expectMsg(ids)
    }

    "Delete actors on request" in {
      runtime ! DeleteAllActors()
      expectNoMsg()

      val json1 = parse("""{"type": "httpserver" }""").asInstanceOf[JObject]
      runtime ! CreateActor(json1)
      val id1 = receiveOne(500.millisecond).asInstanceOf[Option[Long]]

      runtime ! Delete(id1.getOrElse(-1))
      expectNoMsg()

      runtime ! ListActors()
      expectMsg(List())

    }
  }
}