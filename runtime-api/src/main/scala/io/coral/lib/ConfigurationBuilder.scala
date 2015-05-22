package io.coral.lib

import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Helper class to obtain properties, but have application defined properties already added
 * E.g. Kafka uses java properties for producer and consumer instantiation
 * @param path configuration path (e.g. "kafka.consumer")
 */
class ConfigurationBuilder(path: String) {

  private lazy val config: Config = ConfigFactory.load.getConfig(path)

  def properties: Properties = new ConfigProperties(config)

}

/**
 * Read properties from configuration and
 * - Make these accessible as Properties
 * - Allow properties to be overwritten
 * @param config base configuration
 */
private final class ConfigProperties(config: Config) extends Properties() {

  override def getProperty(name: String): String =
    if (super.containsKey(name)) super.getProperty(name)
    else config.getString(name)

}
