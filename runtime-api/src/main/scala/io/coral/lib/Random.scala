package io.coral.lib

import org.uncommons.maths.random.MersenneTwisterRNG

object Random extends Random(new MersenneTwisterRNG()) {

  def apply(source: java.util.Random) = new Random(source)

}

class Random(source: java.util.Random) {

  def uniform(): Stream[Double] = {
    Stream.continually(source.nextDouble())
  }

  def uniform(min: Double, max: Double): Stream[Double] = {
    val scale = max - min
    Stream.continually(min + scale * source.nextDouble())
  }

  def binomial(p: Double): Stream[Boolean] = {
    val uniformStream = uniform(0.0, 1.0)
    uniformStream.map(x => x < p)
  }

}

