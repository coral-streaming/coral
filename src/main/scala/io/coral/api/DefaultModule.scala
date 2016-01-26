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

package io.coral.api

import com.typesafe.config.Config
import io.coral.actors.ActorPropFactory
import scaldi.Module
import scala.collection.JavaConversions._
import io.coral.actors.DefaultActorPropFactory

class DefaultModule(config: Config) extends Module {
	private val ActorPropFactoriesConfigPath = "injections.actorPropFactories"
	bind[List[ActorPropFactory]] to createActorPropFactories

	private def createActorPropFactories: List[ActorPropFactory] = {
		getActorPropFactoryClassNames.map(Class.forName(_).newInstance.asInstanceOf[ActorPropFactory])
	}

	private def getActorPropFactoryClassNames: List[String] = {
		val additionalClassNames = if (config.hasPath(ActorPropFactoriesConfigPath)) {
			(config getStringList ActorPropFactoriesConfigPath).toList
		} else {
			List()
		}

		classOf[DefaultActorPropFactory].getName :: additionalClassNames
	}
}
