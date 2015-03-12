package io.coral.actors

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit, ImplicitSender}
import akka.util.Timeout
import io.coral.actors.Messages.{CreateActor, GetActorPath, RegisterActor, DeleteAllActors}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.actor.ActorPath

class TestThresholdActor(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout = Timeout(100.millis)
  def this() = this(ActorSystem("TestThreshold"))
  val runtime = system.actorOf(Props[RuntimeActor], "coral")

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }
  
  "A ThresholdActor" must {
    val createJson = parse(
      """{ "type": "threshold", "params":
      |{ "key": "key1", "threshold": 10.5 } }"""
      .stripMargin).asInstanceOf[JObject]
    runtime ! CreateActor(createJson)
    expectMsg(Some(1))
    
    val result = (runtime ? GetActorPath(1)).mapTo[Option[ActorPath]]
    val threshold = system.actorSelection(Await.result(result, timeout.duration).get)
    threshold ! RegisterActor(testActor.actorRef)
    
    "Emit when equal to the threshold" in {
      val json = parse("""{"key1": 10.5}""").asInstanceOf[JObject]
      threshold ! json
      expectMsg(parse("""{"key1": 10.5, "thresholdReached": "key1"}"""))
    }
    
    "Emit when higher than the threshold" in {
      val json = parse("""{"key1": 10.7}""").asInstanceOf[JObject]
      threshold ! json
      expectMsg(parse("""{"key1": 10.7, "thresholdReached": "key1"}"""))
    }
    
    "Not emit when higher than the threshold for another key" in {
      val json = parse("""{"key2": 10.7}""").asInstanceOf[JObject]
      threshold ! json
      expectNoMsg
    }
    
    "Not emit when lower than the threshold" in {
      val json = parse("""{"key1": 10.4}""").asInstanceOf[JObject]
      threshold ! json
      expectNoMsg
    }
    
  }
}