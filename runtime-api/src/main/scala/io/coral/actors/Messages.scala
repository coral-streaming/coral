package io.coral.actors

import akka.actor.{ActorRef, ActorPath}
import org.json4s._

object Messages {
    // RuntimeActor messages
    case class CreateActor(json: JObject)
    case class RegisterActorPath(id: Long, path: ActorPath)
    case class UnregisterActorId(id: Long)
    case class GetCount()
    case class ListActors()
    case class GetActorPath(id: Long)
    case class Delete(id: Long)
    case class DeleteAllActors()

    // CoralActor messages
    case class Get()

    // Execute trigger and emit for given value
    case class Shunt(json: JObject)

    // Get single field from state map
    case class GetField(field: String)
    case class GetFieldBy(field: String, by:String)

    case class ListFields()
    case class RegisterActor(r: ActorRef)
    case class UpdateProperties(json:JObject)
    case class GetProperties()

    case object TimeoutEvent
}