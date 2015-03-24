package io.coral.actors

import akka.actor.{ActorRef, ActorPath}
import org.json4s._

object Messages {
	// Generic messages


	// RuntimeActor messages
	case class CreateActor(json: JObject)
	case class RegisterActorPath(id: Long, path: ActorPath)
	case class GetCount()
	case class ListActors()
	case class GetActorPath(id: Long)
	case class Delete(id: Long)
	case class DeleteAllActors()

	// CoralActor messages
	case class Get()
	case class Shunt(json: JObject)
	case class GetField(field:String)
	case class ListFields()
	case class RegisterActor(r: ActorRef)
	case class UpdateProperties(json:JObject)
	case class GetProperties()

	case object TimeoutEvent

    case class Trigger(json: JObject)
    case class Emit()
}