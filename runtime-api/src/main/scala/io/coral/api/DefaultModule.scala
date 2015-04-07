package io.coral.api

import akka.actor.ActorSystem
import io.coral.actors.ActorPropFactory
import scaldi.Module
import scala.collection.JavaConversions._
import io.coral.actors.DefaultActorPropFactory

class DefaultModule(implicit system: ActorSystem) extends Module {
  private val ActorPropFactoriesConfigPath = "injections.actorPropFactories"

  bind[List[ActorPropFactory]] to createActorPropFactories

  private def createActorPropFactories: List[ActorPropFactory] = {
    getActorPropFactoryClassNames.map(Class.forName(_).newInstance.asInstanceOf[ActorPropFactory])
  }

  private def getActorPropFactoryClassNames: List[String] = {
    val additionalClassNames = if (system.settings.config.hasPath(ActorPropFactoriesConfigPath)) {
      (system.settings.config getStringList ActorPropFactoriesConfigPath).toList
    } else {
      List()
    }

    classOf[DefaultActorPropFactory].getName :: additionalClassNames
  }
}
