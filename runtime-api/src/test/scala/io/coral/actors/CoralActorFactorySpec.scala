package io.coral.actors

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestKit
import io.coral.actors.transform.GroupByActor
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scaldi.Module

class CoralActorFactorySpec(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("testSystem"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "The CoralActorFactory" should {

     "create no Props when no corresponding actor is defined" in {
       implicit val injector = new Module {}
       val json = parse(""""{"type":"nonexisting"}""")
       assert(CoralActorFactory.getProps(json) == None)
     }

     "create Props when the actor is defined in a factory" in {
       implicit val injector = new Module {
         bind[List[ActorPropFactory]] to List(new FirstActorPropFactory())
       }
       val json = parse("""{ "type": "actorOne" }""")
       assert(CoralActorFactory.getProps(json).get.actorClass == classOf[ActorOne])
     }

     "create Props when the actor is defined in one of the given factories" in {
       implicit val injector = new Module {
         bind[List[ActorPropFactory]] to List(new FirstActorPropFactory(), new SecondActorPropFactory())
       }
       val json = parse("""{ "type": "actorTwo" }""")
       assert(CoralActorFactory.getProps(json).get.actorClass == classOf[ActorTwo])
     }

     "create Props for the first actor found in one of the given factories" in {
       implicit val injector = new Module {
         bind[List[ActorPropFactory]] to List(new FirstActorPropFactory(), new SecondActorPropFactory())
       }
       val json = parse("""{ "type": "actorOne" }""")
       assert(CoralActorFactory.getProps(json).get.actorClass == classOf[ActorOne])
     }

    "create a GroupActor when the type is group" in {
      val json = parse("""{ "group": {"by": "value" } }""")
      implicit val injector = new Module {}
      assert(CoralActorFactory.getProps(json).get.actorClass == classOf[GroupByActor])
    }
  }
}

class FirstActorPropFactory extends ActorPropFactory {
  override def getProps(actorType: String, params: JValue): Option[Props] = {
    actorType match {
      case "actorOne" => Some(Props(classOf[ActorOne]))
      case _ => None
    }
  }
}

class SecondActorPropFactory extends ActorPropFactory {
  override def getProps(actorType: String, params: JValue): Option[Props] = {
    actorType match {
      case "actorOne" => Some(Props(classOf[ActorOneAlternative]))
      case "actorTwo" => Some(Props(classOf[ActorTwo]))
      case _ => None
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