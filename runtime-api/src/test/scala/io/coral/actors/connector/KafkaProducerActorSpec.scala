package io.coral.actors.connector

import java.util.Properties

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import kafka.producer.{KeyedMessage, Producer}
import org.json4s.JsonAST.{JValue, JObject}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike, Matchers}
import scala.collection.mutable

class KafkaProducerActorSpec(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("KafkaProducerActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A KafkaProducerActor" should {
    "not create create an actor when the definition is incorrect" in {
      val constructor = parse(
        """{ "type": "actors",
          |"attributes": {
          |"type": "kafka-producer",
          |"params" : { "topic": "test" }
          |}}""".stripMargin).asInstanceOf[JObject]

      val props = KafkaProducerActor(constructor)
      assert(props == None)
    }

    "create an actor"  in {
      val constructor = parse(
        """{ "type": "actors",
          |"attributes": {
          |"type": "kafka-producer",
          |"params" : { "topic": "test", "kafka": {} }
          |}}""".stripMargin).asInstanceOf[JObject]

      val props = KafkaProducerActor(constructor)
      props.get.actorClass should be(classOf[KafkaProducerActor])
    }
  }

  "use the provided parameters and the configured parameters to create the producer" in {
    val constructor = parse(
      """{ "type": "actors",
        |"attributes": {
        |"type": "kafka-producer",
        |"params" : { "topic": "test", "kafka": {"metadata.broker.list": "host1:80,host2:81"} }
        |}}""".stripMargin).asInstanceOf[JObject]

    val props = MyKafkaProducerActor(constructor).get

    val actorRef = TestActorRef[MyKafkaProducerActor](props)
    assert(actorRef.underlyingActor.topic == "test")
    val properties = actorRef.underlyingActor.receivedProperties
    assert(properties.getProperty("metadata.broker.list") == "host1:80,host2:81")

    // Property read from application.conf
    assert(properties.getProperty("producer.type") == "async")
  }

  "send the JSON provided without a key to Kafka" in {
    val messageJson = """{"key1": "value1", "key2": "value2"}"""

    val keyedMessage = sendMessage(None, messageJson)

    assert(keyedMessage.topic == "test")
    assert(keyedMessage.hasKey == false)
    assert(parse(new String(keyedMessage.message, "UTF-8")) == parse(messageJson))
  }

  "send the JSON provided with a key to Kafka" in {
    val messageJson = """{"key3": "value3", "key4": "value4"}"""

    val keyedMessage = sendMessage(Some("key"), messageJson)

    assert(keyedMessage.key == "key")
    assert(keyedMessage.topic == "test")
    assert(parse(new String(keyedMessage.message, "UTF-8")) == parse(messageJson))
  }

  private def sendMessage(key: Option[String], messageJson: String) = {
    val constructor = parse(
      """{ "type": "actors",
        |"attributes": {
        |"type": "kafka-producer",
        |"params" : { "topic": "test", "kafka": {} }
        |}}""".stripMargin).asInstanceOf[JObject]

    val props = MyKafkaProducerActor(constructor).get

    val actorRef = TestActorRef[MyKafkaProducerActor](props)

    val keyJson = key match {
      case Some(key) => s""""key": "${key}", """
      case None => ""
    }
    val triggerJson = parse(s"""{${keyJson}"message": ${messageJson}}""").asInstanceOf[JObject]

    actorRef.underlyingActor.trigger(triggerJson)

    val argumentCaptor = ArgumentCaptor.forClass(classOf[KeyedMessage[String, Array[Byte]]])
    verify(MyKafkaProducerActor.producer).send(argumentCaptor.capture())
    reset(MyKafkaProducerActor.producer)

    val keyedMessages = argumentCaptor.getAllValues
    assert(keyedMessages.size == 1)

    // The following construction is necessary because capturing of parameters with Mockito, Scala type interference, and multiple arguments
    // don't work together without explicit casts.
    keyedMessages.get(0).asInstanceOf[mutable.WrappedArray.ofRef[KeyedMessage[String, Array[Byte]]]](0)
  }
}

object MyKafkaProducerActor {
  val producer = mock(classOf[Producer[String, Array[Byte]]])
  def apply(json: JValue): Option[Props] = {
    KafkaProducerActor.getParams(json).map(_ => Props(classOf[MyKafkaProducerActor], json))
  }
}

class MyKafkaProducerActor(json: JObject) extends KafkaProducerActor(json) {
  var receivedProperties: Properties = _

  override def createProducer(properties: Properties) = {
    receivedProperties = properties
    MyKafkaProducerActor.producer
  }
}
