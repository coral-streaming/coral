package io.coral.lib

import scala.Double.NaN
import scala.math.sqrt

object SummaryStatistics {

  def mutable: SummaryStatistics = new MutableSummaryStatistics()

  private class MutableSummaryStatistics()
    extends SummaryStatistics {

    var count = 0L

    var average = NaN

    var variance = NaN

    var min = NaN

    var max = NaN

    def append(value: Double): Unit = count match {
      case 0L =>
        count = 1L
        average = value
        variance = 0.0
        min = value
        max = value
      case _ =>
        val newCount = count + 1L
        val weight = 1.0 / newCount
        val delta = weight * (value - average)
        variance += count * delta * delta - weight * variance
        average += delta
        count = newCount
        min = if (value < min) value else min
        max = if (value > max) value else max
    }

    def reset(): Unit = {
      count = 0L
      average = NaN
      variance = NaN
      min = NaN
      max = NaN
    }

  }

}

trait SummaryStatistics {

  def count: Long

  def average: Double

  def variance: Double

  def populationSd: Double = sqrt(variance)

  def sampleSd: Double =
    if (count > 1L) sqrt(variance * (count.toDouble / (count - 1.0)))
    else NaN

  def min: Double

  def max: Double

  def append(value: Double): Unit

  def reset(): Unit
}

