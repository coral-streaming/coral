package io.coral.actors

import io.coral.actors.transform.HttpClientActor
import org.scalatest.{Matchers, WordSpecLike}
import org.json4s._
import org.json4s.jackson.JsonMethods._

class DefaultActorPropFactorySpec
  extends WordSpecLike
  with Matchers {

  "The DefaultActorPropFactory" should {
    val factory = new DefaultActorPropFactory

    "create the Props for actors defined in the factory" in {
      val json = parse("{}")

      assert(factory.getProps("httpclient", json).get.actorClass == classOf[HttpClientActor])
    }

    "create no Props when no corresponding actor is defined in the factory" in {
      val json = parse("{}")

      assert(factory.getProps("nonexisting", json) == None)
    }
  }
}
