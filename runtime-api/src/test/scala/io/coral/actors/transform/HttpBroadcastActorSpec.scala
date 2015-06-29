package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class HttpBroadcastActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  def this() = this(ActorSystem("HttpServerActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A HttpBroadcastActor" should {

    "Instantiate with any json" in {
      val createJson = parse( """{ "type": "actors", "attributes": {"type": "httpbroadcast" } }""")
      val props = HttpBroadcastActor(createJson)
      assert(props.isDefined)
    }

    "Emit the trigger contents" in {
      val props = HttpBroadcastActor(parse( """{ "type": "actors", "attributes": {"type": "httpbroadcast" } }"""))
      val actor = TestActorRef[HttpBroadcastActor](props.get).underlyingActor
      val json = parse("""{"emit":"whatever"}""")
      val result = actor.simpleEmitTrigger(json.asInstanceOf[JObject])
      result should be(Some(json))
    }

  }
}
