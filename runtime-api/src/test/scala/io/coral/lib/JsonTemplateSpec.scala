package io.coral.lib

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{Matchers, WordSpecLike}

class JsonTemplateSpec extends WordSpecLike with Matchers {

  "JsonTemplate" should {

    "instantiate from a template object" in {
      val templateJson = parse(
        """{ "field1": "abc",
          |  "field2": 123
          |}""".stripMargin)
      val template = JsonTemplate(templateJson.asInstanceOf[JObject])
      template.interpret(parse("{}").asInstanceOf[JObject]) shouldBe templateJson
    }

    "substitute references" in {
      val templateJson = parse(
        """{ "field1": ":abc",
          |  "field2": 123
          |}""".stripMargin)
      val template = JsonTemplate(templateJson.asInstanceOf[JObject])
      val inputJson = parse(
        """{ "def": 456,
          |  "abc": 789
          |}""".stripMargin)
      val outputJson = parse(
        """{ "field1": 789,
          |  "field2": 123
          |}""".stripMargin)
      template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
    }

    "handle nested structure" in {
      val templateJson = parse(
        """{ "a": "ALPHA",
          |  "b": ":beta",
          |  "c": { "d": 123,
          |         "e": ":epsilon"
          |       },
          |  "f": 1,
          |  "g": 1.0
          |}""".stripMargin)
      val template = JsonTemplate(templateJson.asInstanceOf[JObject])
      val inputJson = parse(
        """{ "beta": "xyz",
          |  "epsilon": 987
          |}""".stripMargin)
      val outputJson = parse(
        """{ "a": "ALPHA",
          |  "c": { "d": 123,
          |         "e": 987
          |       },
          |  "f": 1,
          |  "b": "xyz",
          |  "g": 1.0
          |}""".stripMargin)
      template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
    }

  }

}
