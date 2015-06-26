package io.coral.lib

import java.util.Properties

import io.coral.lib.KafkaJsonProducer.KafkaEncoder
import kafka.producer.{KeyedMessage, ProducerConfig, Producer}
import kafka.serializer.Encoder
import kafka.utils.VerifiableProperties
import org.json4s.JsonAST.{JObject, JValue}
import org.json4s.jackson.JsonMethods._

object KafkaJsonProducer {
  type KafkaEncoder = Encoder[JValue]

  def apply() = new KafkaJsonProducer(classOf[JsonEncoder])

  def apply[T <: KafkaEncoder](encoder: Class[T]) = new KafkaJsonProducer(encoder)

}

class KafkaJsonProducer[T <: KafkaEncoder](encoderClass: Class[T]) {
  def createSender(topic: String, properties: Properties): KafkaSender = {
    val props = properties.clone.asInstanceOf[Properties]
    props.put("serializer.class", encoderClass.getName)
    val producer = createProducer(props)
    new KafkaSender(topic, producer)
  }

  def createProducer(props: Properties): Producer[String, JValue] = {
    new Producer[String, JValue](new ProducerConfig(props))
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

class JsonEncoder(verifiableProperties: VerifiableProperties) extends KafkaEncoder {
  override def toBytes(value: JValue): Array[Byte] = {
    compact(value).getBytes("UTF-8")
  }
}
