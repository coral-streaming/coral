package io.coral.actors

import akka.actor.{ActorRef, ActorPath}
import org.json4s._

object Messages {
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
	case class Request(json: JObject)
	case class GetField(field:String)
	case class ListFields()
	case class RegisterActor(r: ActorRef)
	case class UpdateProperties(json:JObject)
	case class GetProperties()
}