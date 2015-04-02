package io.coral.api

import akka.actor.ActorSystem
import io.coral.actors.ActorPropFactory
import scaldi.Module
import scala.collection.JavaConversions._

class DefaultModule(implicit system: ActorSystem) extends Module {
  for (actorPropFactory <- createActorPropFactories) {
    bind[ActorPropFactory] identifiedBy actorPropFactory._1 to actorPropFactory._2
  }

  private def createActorPropFactories: List[(String, ActorPropFactory)] = {
    val classNames:List[String] = (system.settings.config getStringList "injections.actorPropFactories").toList
    classNames.map(name => (name, Class.forName(name).newInstance.asInstanceOf[ActorPropFactory]))
  }
}
