package com.natalinobusa.examples.models

import akka.actor.{ActorPath, ActorRef}
import org.json4s.JObject

object Messages {

  // generic messages for resources
  case class  Get(id:Long)
  case class  Head(id:Long)
  case class  Delete(id:Long)
  case object Get
  case object List
  case object Create
  case object Delete

  // access actor's resources
  case class  GetField(field:String)
  case object ListFields
  case class  RegisterActor(r: ActorRef)
  case class  UpdateProperties(json:JObject)
  case object GetProperties

  // create beads
  case class  CreateActor(json:JObject)
  case class  RegisterActorPath(id:Long, path: ActorPath)
  case object GetCount
  case object ListActors

  //Actors: internal routing and selection
  case class GetActorPath(id:Long)
}
