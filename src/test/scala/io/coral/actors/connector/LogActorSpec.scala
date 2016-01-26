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

import java.io.{PrintStream, ByteArrayOutputStream, FileWriter, File}

import akka.actor.{PoisonPill, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.util.Timeout
import org.json4s.JsonAST.{JString, JNothing, JObject}
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.io.Source

class LogActorSpec(_system: ActorSystem) extends TestKit(_system)
	with ImplicitSender
	with WordSpecLike
	with Matchers
	with BeforeAndAfterAll {
	def this() = this(ActorSystem("LogActorSpec"))
	implicit val timeout = Timeout(1.seconds)

	override def afterAll() {
		TestKit.shutdownActorSystem(system)
	}

	"A LogActor" should {
		"create an actor" in {
			val constructor = parse(
				"""{ "type": "log",
				  |"params" : { "file": "afile"} }
				  |}""".stripMargin).asInstanceOf[JObject]

			val props = LogActor(constructor)
			props.get.actorClass should be(classOf[LogActor])
		}

		"log to the defined file, overwriting the existing file" in {
			val file = File.createTempFile("logactorspec", ".txt")
			val fileWriter = new FileWriter(file)
			fileWriter.write( """{"akey": "avalue"}""")
			fileWriter.close()

			val filePath = compact(JString(file.getAbsolutePath))

			val constructor = parse(
				s"""{ "type": "log",
				   |"params" : { "file": ${filePath} }
				   |}""".stripMargin).asInstanceOf[JObject]

			val props = LogActor(constructor).get
			val actorRef = TestActorRef[LogActor](props)

			val triggerJson1 = parse( """{"key1": "value1", "key2": "value2"}""").asInstanceOf[JObject]
			val triggerJson2 = parse( """{"key3": "value3", "key4": "value4"}""").asInstanceOf[JObject]

			val triggerResult1 = Await.result(actorRef.underlyingActor.trigger(triggerJson1), timeout.duration)
			val triggerResult2 = Await.result(actorRef.underlyingActor.trigger(triggerJson2), timeout.duration)

			assert(triggerResult1 == Some(JNothing))
			assert(triggerResult2 == Some(JNothing))

			val iterator = Source.fromFile(file).getLines

			assert(parse(iterator.next) == triggerJson1)
			assert(parse(iterator.next) == triggerJson2)
			assert(!iterator.hasNext)

			actorRef.underlyingActor.fileWriter.get.close
			file.deleteOnExit()
		}

		"append to the defined file in append mode" in {
			val file = File.createTempFile("logactorspec", ".txt")
			val fileWriter = new FileWriter(file)
			val existingJson = parse( """{"akey": "avalue"}""")
			fileWriter.write(compact(existingJson) + "\n")
			fileWriter.close()

			val filePath = compact(JString(file.getAbsolutePath))

			val constructor = parse(
				s"""{"type": "log",
				   |"params" : { "file": $filePath, "append": true }
				   |}""".stripMargin).asInstanceOf[JObject]

			val props = LogActor(constructor).get
			val actorRef = TestActorRef[LogActor](props)

			val triggerJson1 = parse( """{"key1": "value1", "key2": "value2"}""").asInstanceOf[JObject]
			val triggerJson2 = parse( """{"key3": "value3", "key4": "value4"}""").asInstanceOf[JObject]

			val triggerResult1 = Await.result(actorRef.underlyingActor.trigger(triggerJson1), timeout.duration)
			val triggerResult2 = Await.result(actorRef.underlyingActor.trigger(triggerJson2), timeout.duration)

			assert(triggerResult1.contains(JNothing))
			assert(triggerResult2.contains(JNothing))

			val iterator = Source.fromFile(file).getLines

			assert(parse(iterator.next) == existingJson)
			assert(parse(iterator.next) == triggerJson1)
			assert(parse(iterator.next) == triggerJson2)
			assert(!iterator.hasNext)

			actorRef.underlyingActor.fileWriter.get.close
			file.deleteOnExit()
		}
	}
}
