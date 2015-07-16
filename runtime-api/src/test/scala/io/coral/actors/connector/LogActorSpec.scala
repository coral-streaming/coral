package io.coral.actors.connector

import java.io.{FileWriter, File}

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
    "not create an actor when the definition is incorrect" in {
      val constructor = parse(
        """{ "type": "actors",
          |"attributes": {
          |"type": "log",
          |"params" : {  }
          |}}""".stripMargin).asInstanceOf[JObject]

      val props = LogActor(constructor)
      assert(props == None)
    }

    "create an actor" in {
      val constructor = parse(
        """{ "type": "actors",
          |"attributes": {
          |"type": "log",
          |"params" : { "file": "afile"} }
          |}}""".stripMargin).asInstanceOf[JObject]

      val props = LogActor(constructor)
      props.get.actorClass should be(classOf[LogActor])
    }

    "log to the defined file, overwriting the existing file" in {
      val file = File.createTempFile("logactorspec", ".txt")
      val fileWriter = new FileWriter(file)
      fileWriter.write("""{"akey": "avalue"}""")
      fileWriter.close()

      val filePath = compact(JString(file.getAbsolutePath))

      val constructor = parse(
        s"""{ "type": "actors",
          |"attributes": {
          |"type": "log",
          |"params" : { "file": ${filePath} }
          |}}""".stripMargin).asInstanceOf[JObject]

      val props = LogActor(constructor).get
      val actorRef = TestActorRef[LogActor](props)

      val triggerJson1 = parse("""{"key1": "value1", "key2": "value2"}""").asInstanceOf[JObject]
      val triggerJson2 = parse("""{"key3": "value3", "key4": "value4"}""").asInstanceOf[JObject]

      val triggerResult1 = Await.result(actorRef.underlyingActor.trigger(triggerJson1), timeout.duration)
      val triggerResult2 = Await.result(actorRef.underlyingActor.trigger(triggerJson2), timeout.duration)

      assert(triggerResult1 == Some(JNothing))
      assert(triggerResult2 == Some(JNothing))

      val iterator = Source.fromFile(file).getLines

      assert(parse(iterator.next) == triggerJson1)
      assert(parse(iterator.next) == triggerJson2)
      assert(!iterator.hasNext)

      actorRef.underlyingActor.fileWriter.close
      file.deleteOnExit()
    }

    "append to the defined file in append mode" in {
      val file = File.createTempFile("logactorspec", ".txt")
      val fileWriter = new FileWriter(file)
      val existingJson = parse("""{"akey": "avalue"}""")
      fileWriter.write(compact(existingJson) + System.lineSeparator)
      fileWriter.close()

      val filePath = compact(JString(file.getAbsolutePath))

      val constructor = parse(
        s"""{ "type": "actors",
           |"attributes": {
           |"type": "log",
           |"params" : { "file": ${filePath}, "append": true }
           |}}""".stripMargin).asInstanceOf[JObject]

      val props = LogActor(constructor).get
      val actorRef = TestActorRef[LogActor](props)

      val triggerJson1 = parse("""{"key1": "value1", "key2": "value2"}""").asInstanceOf[JObject]
      val triggerJson2 = parse("""{"key3": "value3", "key4": "value4"}""").asInstanceOf[JObject]

      val triggerResult1 = Await.result(actorRef.underlyingActor.trigger(triggerJson1), timeout.duration)
      val triggerResult2 = Await.result(actorRef.underlyingActor.trigger(triggerJson2), timeout.duration)

      assert(triggerResult1 == Some(JNothing))
      assert(triggerResult2 == Some(JNothing))

      val iterator = Source.fromFile(file).getLines

      assert(parse(iterator.next) == existingJson)
      assert(parse(iterator.next) == triggerJson1)
      assert(parse(iterator.next) == triggerJson2)
      assert(!iterator.hasNext)

      actorRef.underlyingActor.fileWriter.close
      file.deleteOnExit()
    }
  }
}
