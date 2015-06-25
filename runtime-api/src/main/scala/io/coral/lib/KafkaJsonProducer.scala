package io.coral.lib

import java.util.Properties

import io.coral.lib.KafkaJsonProducer.EncoderJson
import kafka.producer.{KeyedMessage, ProducerConfig, Producer}
import kafka.serializer.Encoder
import kafka.utils.VerifiableProperties
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s.jackson.JsonMethods._

object KafkaJsonProducer {
  type EncoderJson = Encoder[JValue]

  def apply() = new KafkaJsonProducer(classOf[JsonEncoder])

  def apply(encoder: Class[Encoder[JValue]]) = new KafkaJsonProducer(encoder)

}

class KafkaJsonProducer(encoderClass: Class[EncoderJson]) {
  def createSender(topic: String, properties: Properties): KafkaSender = {
    val props = properties.clone.asInstanceOf[Properties]
    props.put("serializer.class", encoderClass.getName)
    val producer = new Producer[String, JValue](new ProducerConfig(props))
    new KafkaSender(topic, producer)
  }
}

class KafkaSender(topic: String, producer: Producer[String, JValue]) {
  def send(key: Option[String], message: JObject) = {
    val keyedMessage: KeyedMessage[String, JValue] = key match {
      case Some(key) => new KeyedMessage(topic, key, message)
      case None => new KeyedMessage(topic, message)
    }
    producer.send(keyedMessage)
  }
}

class JsonEncoder(verifiableProperties: VerifiableProperties) extends Encoder[JValue] {
  override def toBytes(value: JValue): Array[Byte] = {
    compact(value).getBytes("UTF-8")
  }
}
