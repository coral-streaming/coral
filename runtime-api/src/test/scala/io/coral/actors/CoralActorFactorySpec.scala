package io.coral.actors

import akka.actor.{Props, Actor}
import io.coral.actors.transform.GroupByActor
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{Matchers, WordSpecLike}
import scaldi.Module

class CoralActorFactorySpec extends WordSpecLike with Matchers {

  implicit val formats = org.json4s.DefaultFormats

  "The CoralActorFactor" should {

    "Provide nothing for invalid JSON" in {
      implicit val injector = new Module {}
      val json = """{}"""
      val props = CoralActorFactory.getProps(parse(json))
      props should be(None)
    }

    "Provide a GroupByActor for any type with group by clause" in {
      val json =
        """{
          |"type": "actors",
          |"subtype": "stats",
          |"params": { "field": "val" },
          |"group": { "by": "somefield" }
          |}""".stripMargin
      implicit val injector = new Module {}
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[GroupByActor])
    }

    "Provide nothing for unknown type" in {
      implicit val injector = new Module {}
      val json = """{"type": "actors", "subtype": "nonexisting"}"""
      val props = CoralActorFactory.getProps(parse(json))
      props should be(None)
    }

    "Provide an actor when the actor is defined in a factory" in {
      implicit val injector = new Module {
        bind[List[ActorPropFactory]] to List(new FirstActorPropFactory())
      }
      val json = """{"type": "actors", "subtype": "actorOne"}"""
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[ActorOne])
    }

    "Provide an actor when the actor is defined in one of the given factories" in {
      implicit val injector = new Module {
        bind[List[ActorPropFactory]] to List(new FirstActorPropFactory(), new SecondActorPropFactory())
      }
      val json = """{"type": "actors", "subtype": "actorTwo"}"""
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[ActorTwo])
    }

    "Provide the first actor found in one of the given factories" in {
      implicit val injector = new Module {
        bind[List[ActorPropFactory]] to List(new FirstActorPropFactory(), new SecondActorPropFactory())
      }
      val json =
        """{"type": "actors", "subtype": "actorOne"}"""
      val props = CoralActorFactory.getProps(parse(json))
      props.get.actorClass should be(classOf[ActorOne])
    }

  }

  class FirstActorPropFactory extends ActorPropFactory {
    override def getProps(actorType: String, params: JValue): Option[Props] = {
      actorType match {
        case "actorOne" => Some(Props[ActorOne])
        case _ => None
      }
    }
  }

  class SecondActorPropFactory extends ActorPropFactory {
    override def getProps(actorType: String, params: JValue): Option[Props] = {
      actorType match {
        case "actorOne" => Some(Props[ActorOneAlternative])
        case "actorTwo" => Some(Props[ActorTwo])
        case _ => None
      }
    }
  }
}

class ActorOne extends Actor {
  override def receive = {
    case _ =>
  }
}

class ActorTwo extends Actor {
  override def receive = {
    case _ =>
  }
}

class ActorOneAlternative extends Actor {
  override def receive = {
    case _ =>
  }
}
