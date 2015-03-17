package io.coral.lib

import Double.NaN

object SummaryStatistics {

  def mutable: SummaryStatistics = new MutableSummaryStatistics()

  private class MutableSummaryStatistics()
    extends SummaryStatistics {

    var _count = 0L

    var _average = NaN

    var _variance = NaN

    var _min = NaN

    var _max = NaN

    def count = _count

    def average = _average

    def variance = _variance

    def min = _min

    def max = _max

    def append(value: Double): Unit = _count match {
      case 0L =>
        _count = 1L
        _average = value
        _variance = 0.0
        _min = value
        _max = value
      case _ =>
        val newCount = count + 1L
        val weight = 1.0 / newCount
        val delta = weight * (value - _average)
        _variance += _count * delta * delta - weight * _variance
        _average += delta
        _count = newCount
        _min = if (value < _min) value else _min
        _max = if (value > _max) value else _max
    }

    def reset(): Unit = {
      _count = 0L
      _average = NaN
      _variance = NaN
      _min = NaN
      _max = NaN
    }

  }

}

trait SummaryStatistics {

  def count: Long

  def average: Double

  def variance: Double

  def min: Double

  def max: Double

  def append(value: Double): Unit

  def reset(): Unit
}

