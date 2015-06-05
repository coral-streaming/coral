package io.coral.lib

import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConverters._

/**
 * Helper class to obtain properties, but have application defined properties already added
 * E.g. Kafka uses java properties for producer and consumer instantiation
 * @param path configuration path (e.g. "kafka.consumer")
 */
case class ConfigurationBuilder(path: String) {

  private lazy val config: Config = ConfigFactory.load.getConfig(path)

  def properties: Properties = {
    val props = new Properties()
    val it = config.entrySet().asScala
    it.foreach { entry => println(s"key=${entry.getKey}, value=${entry.getValue.unwrapped}");props.setProperty(entry.getKey, entry.getValue.unwrapped.toString) }
    props
  }

}
