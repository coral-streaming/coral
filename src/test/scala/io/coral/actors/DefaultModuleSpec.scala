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

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import io.coral.api.DefaultModule
import org.json4s.JsonAST.JValue
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scaldi.Injectable._

class DefaultModuleSpec(_system: ActorSystem) extends TestKit(_system)
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	def this() = this(ActorSystem("testSystem"))

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"The DefaultModule" should {
		"have the DefaultActorPropFactory when no configuration is made" in {
			implicit val module = new DefaultModule(ConfigFactory.empty)
			val actorPropFactories = inject[List[ActorPropFactory]]

			assert(actorPropFactories.size == 1)
			assert(actorPropFactories(0).getClass == classOf[DefaultActorPropFactory])
		}

		"have the DefaultActorPropFactory when a configuration is made" in {
			val config = """injections.actorPropFactories = ["io.coral.actors.AdditionalActorPropFactoryOne"]"""
			implicit val module = new DefaultModule(ConfigFactory.parseString(config))

			val actorPropFactories = inject[List[ActorPropFactory]]

			assert(actorPropFactories.size == 2)
			assert(actorPropFactories(0).getClass == classOf[DefaultActorPropFactory])
			assert(actorPropFactories(1).getClass == classOf[AdditionalActorPropFactoryOne])
		}

		"should have the ActorPropFactories in the defined order" in {
			val config =
				"""injections.actorPropFactories = ["io.coral.actors.AdditionalActorPropFactoryOne",
				  |"io.coral.actors.AdditionalActorPropFactoryTwo"]""".stripMargin
			implicit val module = new DefaultModule(ConfigFactory.parseString(config))

			val actorPropFactories = inject[List[ActorPropFactory]]

			assert(actorPropFactories.size == 3)
			assert(actorPropFactories(0).getClass == classOf[DefaultActorPropFactory])
			assert(actorPropFactories(1).getClass == classOf[AdditionalActorPropFactoryOne])
			assert(actorPropFactories(2).getClass == classOf[AdditionalActorPropFactoryTwo])
		}
	}
}

class AdditionalActorPropFactoryOne extends ActorPropFactory {
	override def getProps(actorType: String, params: JValue): Option[Props] = None
}

class AdditionalActorPropFactoryTwo extends ActorPropFactory {
	override def getProps(actorType: String, params: JValue): Option[Props] = None
}