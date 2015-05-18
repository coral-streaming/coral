package io.coral.lib

import org.json4s.JsonAST._

object JsonTemplate {

  private def isReference(field: String): Boolean = field.startsWith(":")

  private def extractReference(field: String): String = field.substring(1).trim

  private def parse(s: String, json: JObject): JValue = {
    val result = JsonExpressionParser.parse(s, json)
    if (result == JNothing) JNull
    else result
  }

  private def evaluate(json: JObject, node: JValue): JValue = node match {
    case JString(s) =>
      if (isReference(s)) parse(extractReference(s), json)
      else node
    case _ => node
  }

  def validate(template: JObject): Boolean = {
    true // the only thing that could be checked would be expression syntax; so until this is available from the JsonExpressionParser just say yea
  }

  def apply(template: JObject): JsonTemplate = new JsonTemplate(template)

}

class JsonTemplate(val template: JObject) {

  def interpret(input: JObject): JValue = {
    def transformField(field: (String, JValue)): (String, JValue) =
      (field._1, JsonTemplate.evaluate(input, field._2))
    template.mapField(transformField)
  }

}
