package io.coral.api

import akka.actor.ActorSystem
import io.coral.actors.ActorPropFactory
import scaldi.Module
import scala.collection.JavaConversions._
import io.coral.actors.DefaultActorPropFactory

class DefaultModule(implicit system: ActorSystem) extends Module {
  private val ActorPropFactoriesConfigPath = "injections.actorPropFactories"

  for (actorPropFactory <- createActorPropFactories) {
    bind[ActorPropFactory] identifiedBy actorPropFactory._1 to actorPropFactory._2
  }

  private def createActorPropFactories: List[(String, ActorPropFactory)] = {
    getActorPropFactoryClassNames.map(name => (name, Class.forName(name).newInstance.asInstanceOf[ActorPropFactory]))
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
