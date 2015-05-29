package io.coral.lib

import java.util.Properties

import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class KafkaJsonConsumerSpec extends WordSpec with Matchers with MockitoSugar {

  "KafkaJsonConsumer" should {

    "provide a properties builder" in {
      KafkaJsonConsumer.builder shouldBe new ConfigurationBuilder("kafka.consumer")
    }

    "provide a connect method" in {
      val consumer = KafkaJsonConsumer()
      intercept[IllegalArgumentException] {
        consumer.connect(new Properties())
      }
    }

  }

}
