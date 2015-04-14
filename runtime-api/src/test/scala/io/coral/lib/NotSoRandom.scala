package io.coral.lib

object NotSoRandom {

  def apply(xs: Double*): java.util.Random = new NotSoRandomDouble(xs: _*)

}

// Note: we do not test the underlying source of random numbers here
class NotSoRandomDouble(xs: Double*) extends java.util.Random {
  var i = -1

  override def nextDouble(): Double = {
    i += 1
    if (i < xs.length) xs(i)
    else Double.NaN // don't throw an exception as stream may compute one ahead
  }
}
