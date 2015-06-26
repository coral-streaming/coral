package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import org.json4s.JsonAST.JValue
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

  def this() = this(ActorSystem("JsonActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(100.millis)

  def createJsonActor(json: JValue): JsonActor = {
    val props = JsonActor(json).get
    val actorRef = TestActorRef[JsonActor](props)
    actorRef.underlyingActor
  }

  def apiJson(s: String): JValue = parse( s"""{"type": "actors", "attributes": $s }""")

  "JsonActor" should {

    "have a standard coral props supplier" in {
      val json = apiJson( """{ "type": "json", "params": { "template": {} } }""")
      val props = JsonActor(json).get
      props.actorClass shouldBe classOf[JsonActor]
    }

    "have jsonDef return the construction" in {
      val json = apiJson( """{ "type": "json", "params": { "template": {} } }""")
      val actor = createJsonActor(json)
      actor.jsonDef shouldBe json
    }

    "read the template parameter" in {
      val template = """{ "a": "someReference" }"""
      val json = apiJson( s"""{ "type": "json", "params": { "template": ${template} } }""")
      val actor = createJsonActor(json)
      actor.template.template shouldBe parse(template)
    }

    "emit the json based on template" in {
      val templateJson =
        """{ "a": "ALPHA",
          |  "b": "${beta}",
          |  "c": { "d": 123,
          |         "e": "${epsilon}"
          |       },
          |  "f": 1,
          |  "g": 1.0
          |}""".stripMargin
      val json = apiJson( s"""{ "type": "json", "params": { "template": ${templateJson} } }""")
      val actor = createJsonActor(json)
      val triggerJson = parse(
        """{ "beta": "xyz",
          |  "epsilon": 987
          |}""".stripMargin)
      val expectedJson = parse(
        """{ "a": "ALPHA",
          |  "c": { "d": 123,
          |         "e": 987
          |       },
          |  "f": 1,
          |  "b": "xyz",
          |  "g": 1.0
          |}""".stripMargin)
      actor.emit(triggerJson.asInstanceOf[JObject]) shouldBe expectedJson
    }

  }

}
