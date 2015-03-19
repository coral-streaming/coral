package io.coral.lib

import org.scalatest.{Matchers, WordSpecLike}

class SummaryStatisticsSpec extends WordSpecLike with Matchers {

  "SummaryStatistics should" should {
    "provide a count initialized to 0L" in {
      val stats = SummaryStatistics.mutable
      stats.count should equal(0L)
    }

    "allow update with a new value" in {
      val stats = SummaryStatistics.mutable
      stats.append(2.7)
      stats.count should be(1L)
      stats.append(2.7)
      stats.count should be(2L)
    }

    "allow reset to initial state" in {
      val stats = SummaryStatistics.mutable
      stats.append(2.7)
      stats.append(2.7)
      stats.reset()
      stats.count should be(0L)
    }

    "provide the average (initialize/update/reset correctly)" in {
      val stats = SummaryStatistics.mutable
      assert(stats.average.isNaN)
      stats.append(2.7)
      stats.average should be(2.7)
      stats.append(-1.7)
      stats.average should be(0.5)
      stats.append(2.0)
      stats.average should be(1.0)
      stats.reset()
      assert(stats.average.isNaN)
    }

    "provide the variance (initialize/update/reset correctly)" in {
      val stats = SummaryStatistics.mutable
      assert(stats.variance.isNaN)
      stats.append(2.0)
      stats.variance should be(0.0)
      stats.append(6.0)
      stats.variance should be(4.0)
      stats.append(1.0)
      stats.variance should be(14.0 / 3.0)
      stats.reset()
      assert(stats.variance.isNaN)
    }

    "provide the minimum (initialize/update/reset correctly)" in {
      val stats = SummaryStatistics.mutable
      assert(stats.min.isNaN)
      stats.append(6.0)
      stats.min should be(6.0)
      stats.append(2.5)
      stats.min should be(2.5)
      stats.append(3.0)
      stats.min should be(2.5)
      stats.append(-1.0)
      stats.min should be(-1.0)
      stats.reset()
      assert(stats.min.isNaN)
    }

    "provide the maximum (initialize/update/reset correctly)" in {
      val stats = SummaryStatistics.mutable
      assert(stats.max.isNaN)
      stats.append(6)
      stats.max should be(6.0)
      stats.append(5.1)
      stats.max should be(6.0)
      stats.append(6.01)
      stats.max should be(6.01)
      stats.reset()
      assert(stats.max.isNaN)
    }

  }

}
