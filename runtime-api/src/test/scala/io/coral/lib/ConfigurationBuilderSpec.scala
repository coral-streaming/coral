package io.coral.lib

import com.typesafe.config.ConfigException
import org.scalatest.{Matchers, WordSpecLike}

class ConfigurationBuilderSpec extends WordSpecLike with Matchers {

  "ConfigurationBuilder" should {

    "read application.conf" in {
      val builder = new ConfigurationBuilder("test.builder")
      val properties = builder.properties
      properties.getProperty("someProperty") shouldBe "someValue"
    }

    "allow to add properties" in {
      val builder = new ConfigurationBuilder("test.builder")
      val properties = builder.properties
      properties.setProperty("vis", "blub")
      properties.getProperty("someProperty") shouldBe "someValue"
      properties.getProperty("vis") shouldBe "blub"
      properties.size shouldBe 2
    }

    "allow to replace properties" in {
      val builder = new ConfigurationBuilder("test.builder")
      val properties = builder.properties
      properties.setProperty("someProperty", "someOtherValue")
      properties.getProperty("someProperty") shouldBe "someOtherValue"
    }

    "throw an exception for missing keys" in {
      val builder = new ConfigurationBuilder("test.builder")
      val properties = builder.properties
      intercept[ConfigException] {
        properties.getProperty("aNonExistingProperty") shouldBe "throwing an exception"
      }
    }

  }

}
