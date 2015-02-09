package io.coral.actors

import scala.collection.immutable.{SortedMap, SortedSet}
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scalaz.OptionT._
import scalaz.{Monad, OptionT}
import scala.reflect.Manifest

case class CreateActor(json: JObject)
case class RegisterActorPath(id: Long, path: ActorPath)
case class GetCount()
case class ListActors()
case class GetActorPath(id: Long)
case class Delete(id: Long)

class RuntimeActor extends Actor with ActorLogging {
	def actorRefFactory = context
	var actors = SortedMap.empty[Long, ActorPath]
	var count = 0L

	def receive = {
		case CreateActor(json) =>
			val props = CoralActorFactory.getProps(json)

			val actorId = props map { p =>
				log.warning(p.toString)
				count += 1
				val id = count
				val actor = actorRefFactory.actorOf(p, s"$id")
				actors += (id -> actor.path)
				id
			}

			sender ! actorId
		case RegisterActorPath(id, path) =>
			actors += (id -> path)
		case GetCount() =>
			count += 1
			sender ! Some(count)
		case ListActors() =>
			sender ! actors.keys.toList
		case GetActorPath(id) =>
			val path = actors.get(id)
			log.info(s"streams get stream id $id, path ${path.toString} ")
			sender ! path
	}
}