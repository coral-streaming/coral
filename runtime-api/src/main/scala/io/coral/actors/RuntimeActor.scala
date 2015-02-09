package io.coral.actors

import io.coral.actors.Messages._
import scala.collection.immutable.SortedMap
import akka.actor._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._

class RuntimeActor extends Actor with ActorLogging {
	def actorRefFactory = context
	var actors = SortedMap.empty[Long, ActorPath]
	var count = 0L

	def receive = {
		case CreateActor(json) =>
			val props = CoralActorFactory.getProps(json)

			val actorId = props map { p =>
				log.info(p.toString)
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
			// TODO: Natalino, why is this needed?
			count += 1
			sender ! Some(count)
		case ListActors() =>
			if (actors.keys.size == 0) {
				sender ! List()
			} else {
				sender ! actors.keys.toList
			}
		case Delete(id: Long) =>
			actors.get(id).map { a => actorRefFactory.actorSelection(a) ! PoisonPill }
			actors -= id
		case DeleteAllActors() =>
			actors.foreach { path => actorRefFactory.actorSelection(path._2) ! PoisonPill }
			actors = SortedMap.empty[Long, ActorPath]
			count = 0
			log.info(context.children.size.toString)
		case GetActorPath(id) =>
			val path = actors.get(id)
			log.info(s"streams get stream id $id, path ${path.toString} ")
			sender ! path
	}
}