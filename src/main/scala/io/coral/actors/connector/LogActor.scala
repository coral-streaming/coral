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

package io.coral.actors.connector

import java.io.FileWriter
import akka.actor.{ActorLogging, Props}
import io.coral.actors.CoralActor
import org.json4s.JsonAST.JObject
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.concurrent.Future

/**
 * This log actor is meant for testing a defined pipeline,
 * not for production use. The intended production use is
 * to write to Kafka using the Kafka consumer.
 */
object LogActor {
	implicit val formats = org.json4s.DefaultFormats

	def getParams(json: JValue) = {
		val file = (json \ "params" \ "file").extractOpt[String]
		val append = (json \ "params" \ "append").extractOpt[Boolean]
		Some((file, append getOrElse false))
	}

	def apply(json: JValue): Option[Props] = {
		getParams(json).map(_ => Props(classOf[LogActor], json))
	}
}

class LogActor(json: JObject) extends CoralActor(json) with ActorLogging {
	val (file, append) = LogActor.getParams(json).get
	var fileWriter: Option[FileWriter] = None

	override def preStart() = {
		if (file.isDefined) {
			fileWriter = Some(new FileWriter(file.get, append))
		}
	}

	override def postStop() = {
		fileWriter match {
			case None =>
			case Some(f) => f.close()

		}
	}

	override def trigger = {
		json => Future {
			fileWriter match {
				case None =>
					log.info(compact(json))
				case Some(f) =>
					f.write(compact(json) + "\n")
					f.flush()
			}

			Some(JNothing)
		}
	}
}