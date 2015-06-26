package io.coral.actors.connector

import java.util.Properties

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import io.coral.lib.{KafkaSender, JsonEncoder, KafkaJsonProducer}
import io.coral.lib.KafkaJsonProducer.KafkaEncoder
import kafka.producer.Producer
import org.json4s.JsonAST.{JValue, JObject}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike, Matchers}

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

    "create an actor" in {
      val constructor = parse(
        """{ "type": "actors",
          |"attributes": {
          |"type": "kafka-producer",
          |"params" : { "topic": "test", "kafka": {} }
          |}}""".stripMargin).asInstanceOf[JObject]

      val props = KafkaProducerActor(constructor)
      props.get.actorClass should be(classOf[KafkaProducerActor[KafkaEncoder]])
    }

    "use the provided parameters and the configured parameters" in {
      val constructor = parse(
        """{ "type": "actors",
          |"attributes": {
          |"type": "kafka-producer",
          |"params" : { "topic": "test", "kafka": {"metadata.broker.list": "host1:80,host2:81"} }
          |}}""".stripMargin).asInstanceOf[JObject]

      val props = KafkaProducerActor(constructor).get

      val actorRef = TestActorRef[KafkaProducerActor[KafkaEncoder]](props)
      assert(actorRef.underlyingActor.topic == "test")
      val properties = actorRef.underlyingActor.properties
      assert(properties.getProperty("metadata.broker.list") == "host1:80,host2:81")

      // Property read from application.conf
      assert(properties.getProperty("producer.type") == "async")
    }

    "send the JSON provided to Kafka" in {
      val constructor = parse(
        """{ "type": "actors",
          |"attributes": {
          |"type": "kafka-producer",
          |"params" : { "topic": "test", "kafka": {} }
          |}}""".stripMargin).asInstanceOf[JObject]
      val messageJson = """{"key3": "value3", "key4": "value4"}"""
      val keyJson = s""""key": "${key}", """
      val triggerJson = parse( s"""{${keyJson}"message": ${messageJson}}""").asInstanceOf[JObject]

      val producer = new MyKafkaJsonProducer
      val props = Props(classOf[KafkaProducerActor[KafkaEncoder]], constructor, producer)

      val actorRef = TestActorRef[KafkaProducerActor[KafkaEncoder]](props)

      actorRef.underlyingActor.trigger(triggerJson)

      assert(producer.sender.receivedKey == Some("key"))
      assert(producer.sender.receivedMessage == parse(messageJson))
    }
  }
}

class MyKafkaJsonProducer extends KafkaJsonProducer(classOf[JsonEncoder]) {
  var sender: MyKafkaSender = _

  override def createSender(topic: String, properties: Properties): KafkaSender = {
    val producer = mock(classOf[Producer[String, JValue]])
    sender = new MyKafkaSender(topic, producer)
    sender
  }
}

class MyKafkaSender(topic: String, producer: Producer[String, JValue]) extends KafkaSender(topic, producer) {
  var receivedKey: Option[String] = _
  var receivedMessage: JObject = _

  override def send(key: Option[String], message: JObject) = {
    receivedKey = key
    receivedMessage = message
  }
}
