package io.coral.lib

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

    "properties (Java) return null for missing keys" in {
      val builder = new ConfigurationBuilder("test.builder")
      val properties = builder.properties
      properties.getProperty("aNonExistingProperty") shouldBe null
    }

  }

}
