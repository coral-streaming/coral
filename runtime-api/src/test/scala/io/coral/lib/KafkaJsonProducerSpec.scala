package io.coral.lib

import java.util.Properties

import io.coral.lib.KafkaJsonProducer.KafkaEncoder
import kafka.utils.VerifiableProperties
import org.json4s.JsonAST.{JObject, JValue}
import org.scalatest.{Matchers, WordSpec}
import org.json4s.jackson.JsonMethods._
import kafka.producer.{ProducerConfig, KeyedMessage, Producer}
import org.mockito.{Mockito, ArgumentCaptor}
import org.mockito.Mockito._
import scala.collection.mutable

class KafkaJsonProducerSpec extends WordSpec with Matchers {
  "A KafkaJsonProducer" should {
    "create a KafkaJsonProducer with the JsonEncoder" in {
      val producer = KafkaJsonProducer()
      assert(producer.getClass == classOf[KafkaJsonProducer[JsonEncoder]])
    }

    "create a KafkaJsonProducer with the specified Encoder" in {
      val producer = KafkaJsonProducer(classOf[MyEncoder])
      assert(producer.getClass == classOf[KafkaJsonProducer[MyEncoder]])
    }

    "create a sender" in {
      val producer = new MyKafkaJsonProducer
      producer.createSender("topic", new Properties)
      val serializer = producer.receivedProperties.get("serializer.class")
      assert(serializer == classOf[MyEncoder].getName)
    }
  }

  "A KafkaSender" should {
    "send the JSON provided without a key to Kafka" in {
      val messageJson = """{"key1": "value1", "key2": "value2"}"""

      val keyedMessage = sendMessage(None, messageJson)

      assert(keyedMessage.topic == "test")
      assert(keyedMessage.hasKey == false)
      assert(keyedMessage.message == parse(messageJson))
    }

    "send the JSON provided with a key to Kafka" in {
      val messageJson = """{"key3": "value3", "key4": "value4"}"""

      val keyedMessage = sendMessage(Some("key"), messageJson)

      assert(keyedMessage.key == "key")
      assert(keyedMessage.topic == "test")
      assert(keyedMessage.message == parse(messageJson))
    }
  }

  "A JsonEncoder" should {
    "encode the provided json" in {
      val json = """{"key1": "value1"}"""
      val encoder = new JsonEncoder(new VerifiableProperties)
      val result = encoder.toBytes(parse(json))
      assert(parse(new String(result, "UTF-8")) == parse(json))
    }
  }

  private def sendMessage(key: Option[String], messageJson: String): KeyedMessage[String, JValue] = {
    val producer = Mockito.mock(classOf[Producer[String, JValue]])
    val sender = new KafkaSender("test", producer)
    sender.send(key, parse(messageJson).asInstanceOf[JObject])

    val argumentCaptor = ArgumentCaptor.forClass(classOf[KeyedMessage[String, JValue]])
    verify(producer).send(argumentCaptor.capture())

    val keyedMessages = argumentCaptor.getAllValues
    assert(keyedMessages.size == 1)

    // The following construction is necessary because capturing of parameters with Mockito, Scala type interference, and multiple arguments
    // don't work together without explicit casts.
    keyedMessages.get(0).asInstanceOf[mutable.WrappedArray.ofRef[KeyedMessage[String, JValue]]](0)
  }
}

class MyEncoder(verifiableProperties: VerifiableProperties) extends KafkaEncoder {
  override def toBytes(value: JValue): Array[Byte] = {
    Array()
  }
}

class MyKafkaJsonProducer extends KafkaJsonProducer(classOf[MyEncoder]) {
  var receivedProperties: Properties = _

  override def createProducer(props: Properties): Producer[String, JValue] = {
    receivedProperties = props
    Mockito.mock(classOf[Producer[String, JValue]])
  }
}
