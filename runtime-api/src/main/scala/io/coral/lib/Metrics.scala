package io.coral.lib

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import com.codahale.metrics.graphite.{GraphiteReporter, Graphite}
import com.codahale.metrics.{MetricFilter, MetricRegistry}
import nl.grons.metrics.scala.{MetricName, InstrumentedBuilder}

object Metrics {
  /** The application wide metrics registry. */
  val metricRegistry = new MetricRegistry()

  def startReporter(system: ActorSystem) = {
    val reporter = createReporter(system)
    val graphitePollingSeconds = system.settings.config getInt "graphite.pollingSeconds"
    val graphiteEnabled = system.settings.config getBoolean "graphite.enabled"

    if (graphiteEnabled) {
      reporter.start(graphitePollingSeconds, TimeUnit.SECONDS)
    }
  }

  private def createReporter(system: ActorSystem) = {
    val graphiteHost = system.settings.config getString "graphite.host"
    val graphitePort = system.settings.config getInt "graphite.port"
    val graphitePrefix = system.settings.config getString "graphite.prefix"

    val graphite = new Graphite(new InetSocketAddress(graphiteHost, graphitePort))
    GraphiteReporter.forRegistry(metricRegistry)
      .prefixedWith(graphitePrefix)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .filter(MetricFilter.ALL)
      .build(graphite)
  }
}

trait Metrics extends InstrumentedBuilder {
  // Override the base name so the class name isn't used as a prefix.
  override lazy val metricBaseName = MetricName("")

  val metricRegistry = Metrics.metricRegistry
}