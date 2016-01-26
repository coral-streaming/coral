/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.actors

import akka.actor.{Props, Actor}
import io.coral.actors.transform.GroupByActor
import org.json4s._
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpecLike}
import scaldi.Module

@RunWith(classOf[JUnitRunner])
class CoralActorFactorySpec extends WordSpecLike with Matchers {
	implicit val formats = org.json4s.DefaultFormats

	"The CoralActorFactory" should {
		"Provide nothing for invalid JSON" in {
			implicit val injector = new Module {}
			val json = """{}"""
			val props = CoralActorFactory.getProps(parse(json))
			props should be(None)
		}

		"Provide a GroupByActor for any type with group by clause" in {
			val json =
				"""{ "type": "stats", "params": { "field": "val" }, "group": { "by": "somefield" }}"""
			val parsed = parse(json)
			implicit val injector = new Module {}
			val props = CoralActorFactory.getProps(parsed)
			props.get.actorClass should be(classOf[GroupByActor])
		}

		"Provide nothing for unknown type" in {
			implicit val injector = new Module {}
			val json = """{"type": "actors", "attributes": {"type": "nonexisting"}}"""
			val props = CoralActorFactory.getProps(parse(json))
			props should be(None)
		}

		"Provide an actor when the actor is defined in a factory" in {
			implicit val injector = new Module {
				bind[List[ActorPropFactory]] to List(new FirstActorPropFactory())
			}
			val json = """{"type": "actorOne"}"""
			val props = CoralActorFactory.getProps(parse(json))
			props.get.actorClass should be(classOf[ActorOne])
		}

		"Provide an actor when the actor is defined in one of the given factories" in {
			implicit val injector = new Module {
				bind[List[ActorPropFactory]] to List(new FirstActorPropFactory(), new SecondActorPropFactory())
			}
			val json = """{"type": "actorTwo"}"""
			val props = CoralActorFactory.getProps(parse(json))
			props.get.actorClass should be(classOf[ActorTwo])
		}

		"Provide the first actor found in one of the given factories" in {
			implicit val injector = new Module {
				bind[List[ActorPropFactory]] to List(new FirstActorPropFactory(), new SecondActorPropFactory())
			}
			val json =
				"""{"type": "actorOne"}"""
			val props = CoralActorFactory.getProps(parse(json))
			props.get.actorClass should be(classOf[ActorOne])
		}

	}

	class FirstActorPropFactory extends ActorPropFactory {
		override def getProps(actorType: String, params: JValue): Option[Props] = {
			actorType match {
				case "actorOne" => Some(Props[ActorOne])
				case _ => None
			}
		}
	}

	class SecondActorPropFactory extends ActorPropFactory {
		override def getProps(actorType: String, params: JValue): Option[Props] = {
			actorType match {
				case "actorOne" => Some(Props[ActorOneAlternative])
				case "actorTwo" => Some(Props[ActorTwo])
				case _ => None
			}
		}
	}

}

class ActorOne extends Actor {
	override def receive = {
		case _ =>
	}
}

class ActorTwo extends Actor {
	override def receive = {
		case _ =>
	}
}

class ActorOneAlternative extends Actor {
	override def receive = {
		case _ =>
	}
}
