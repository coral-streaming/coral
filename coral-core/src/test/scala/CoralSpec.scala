package io.coral.core

import org.scalatest.WordSpec

class CoralSpec extends WordSpec {

  "The Coral API" when {
    "started" should {
      "provide  streaming analytics " in {
        assert(Thing.streaming==true)
      }

      "be fun and intuitive" in {
        assert(Thing.fun==true)
      }

      "provide dataflow capabilities via a json http API" in {
        assert(Thing.jsonapi==true)
      }

    }
  }
}