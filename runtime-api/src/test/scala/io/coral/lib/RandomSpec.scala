package io.coral.lib

import org.scalatest.{Matchers, WordSpecLike}

class RandomSpec extends WordSpecLike with Matchers {

  "The Random object" should {

    "provide a stream of uniformly distributed doubles" in {
      val source = NotSoRandom(0.3, 0.8)
      val random = new Random(source)
      val stream = random.uniform()
      stream.head should be(0.3) // as example of simple use case
      stream.take(2).toList should be(List(0.3, 0.8))
    }

    "provide a stream of uniformly distributed doubles with scale" in {
      val source = NotSoRandom(0.3, 0.8)
      val random = new Random(source)
      val stream = random.uniform(2.0, 6.0)
      stream.take(2).toList should be(List(3.2, 5.2))
    }

    "provide a stream of weighted true/false values (binomial values)" in {
      val source = NotSoRandom(0.3, 0.8, 0.299999, 0.0, 1.0)
      val random = new Random(source)
      val stream = random.binomial(0.3)
      stream.take(5).toList should be(List(false, false, true, true, false))
    }

  }

}
