package io.coral.actors.api

import akka.actor.{Props, ActorSystem}
import akka.testkit.TestKit
import com.typesafe.config.{ConfigFactory, Config}
import io.coral.actors.{DefaultActorPropFactory, ActorPropFactory}
import io.coral.api.DefaultModule
import org.json4s.JsonAST.JValue
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scaldi.Injectable._

class DefaultModuleSpec(_system: ActorSystem) extends TestKit(_system)
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("testSystem"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "The DefaultModule" should {
    "have the DefaultActorPropFactory when no configuration is made" in {
      implicit val module = new DefaultModule(ConfigFactory.empty)
      val actorPropFactories = inject [List[ActorPropFactory]]

      assert(actorPropFactories.size == 1)
      assert(actorPropFactories(0).getClass == classOf[DefaultActorPropFactory])
    }

    "have the DefaultActorPropFactory when a configuration is made" in {
      val config = """injections.actorPropFactories = ["io.coral.actors.api.AdditionalActorPropFactoryOne"]"""
      implicit val module = new DefaultModule(ConfigFactory.parseString(config))

      val actorPropFactories = inject [List[ActorPropFactory]]

      assert(actorPropFactories.size == 2)
      assert(actorPropFactories(0).getClass == classOf[DefaultActorPropFactory])
      assert(actorPropFactories(1).getClass == classOf[AdditionalActorPropFactoryOne])
    }

    "should have the ActorPropFactories in the defined order" in {
      val config = """injections.actorPropFactories = ["io.coral.actors.api.AdditionalActorPropFactoryOne", "io.coral.actors.api.AdditionalActorPropFactoryTwo"]"""
      implicit val module = new DefaultModule(ConfigFactory.parseString(config))

      val actorPropFactories = inject [List[ActorPropFactory]]

      assert(actorPropFactories.size == 3)
      assert(actorPropFactories(0).getClass == classOf[DefaultActorPropFactory])
      assert(actorPropFactories(1).getClass == classOf[AdditionalActorPropFactoryOne])
      assert(actorPropFactories(2).getClass == classOf[AdditionalActorPropFactoryTwo])
    }
  }
}

class AdditionalActorPropFactoryOne extends ActorPropFactory {
  override def getProps(actorType: String, params: JValue): Option[Props] = None
}

class AdditionalActorPropFactoryTwo extends ActorPropFactory {
  override def getProps(actorType: String, params: JValue): Option[Props] = None
}