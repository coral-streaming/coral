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

    "substitute references (identified with a :)" in {
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
          |         "e": { "ee": ":epsilon" }
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
          |         "e": { "ee": 987 }
          |       },
          |  "f": 1,
          |  "b": "xyz",
          |  "g": 1.0
          |}""".stripMargin)
      template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
    }

    "handle expressions cf jsonExpressionParser" in {
      val templateJson = parse(
        """{ "a": ":array[1]",
          |  "b": ":field.sub.subsub",
          |  "c": 1.0
          |}""".stripMargin)
      val template = JsonTemplate(templateJson.asInstanceOf[JObject])
      val inputJson = parse(
        """{ "array": ["a0", "a1", "a2"],
          |  "field": { "sub": { "subsub": 123, "bla": "bla" } },
          |  "epsilon": 987
          |}""".stripMargin)
      val outputJson = parse(
        """{ "a": "a1",
          |  "b": 123,
          |  "c": 1.0
          |}""".stripMargin)
      template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
    }

    "use null when values are not found" in {
      val templateJson = parse(
        """{ "field1": ":abc",
          |  "field2": 123
          |}""".stripMargin)
      val template = JsonTemplate(templateJson.asInstanceOf[JObject])
      val inputJson = parse(
        """{ "def": 456
          |}""".stripMargin)
      val outputJson = parse(
        """{ "field1": null,
          |  "field2": 123
          |}""".stripMargin)
      template.interpret(inputJson.asInstanceOf[JObject]) shouldBe outputJson
    }

  }

}
