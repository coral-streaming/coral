package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class JsonActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("StatsActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(100.millis)

  def createJsonActor(json: JValue): JsonActor = {
    val props = JsonActor(json).get
    val actorRef = TestActorRef[JsonActor](props)
    actorRef.underlyingActor
  }

  "JsonActor" should {

    "have a standard coral props supplier" in {
      val json =  parse( """{ "type": "json", "params": { "template": {} } }""")
      val props = JsonActor(json).get
      props.actorClass shouldBe classOf[JsonActor]
    }

    "have jsonDef return the construction" in {
      val json =  parse( """{ "type": "json", "params": { "template": {} } }""")
      val actor = createJsonActor(json)
      actor.jsonDef shouldBe json
    }

    "have no timer functionality" in {
      val json =  parse( """{ "type": "json", "params": { "template": {} } }""")
      val actor = createJsonActor(json)
      actor.timer shouldBe JNothing
    }

    "have no state" in {
      val json =  parse( """{ "type": "json", "params": { "template": {} } }""")
      val actor = createJsonActor(json)
      actor.state shouldBe Map.empty[String, JValue]
    }

    "read the template parameter" in {
      val template = """{ "a": "someReference" }"""
      val json =  parse( s"""{ "type": "json", "params": { "template": ${template} } }""")
      val actor = createJsonActor(json)
      actor.template shouldBe parse(template)
    }

  }

}
