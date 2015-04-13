package io.coral.coralscript

case class EmitJson(fields: List[EmitJsonField])
case class EmitJsonField(id: Identifier, expr: EmitJsonValue)
case class EmitJsonValue(value: Any)

case class EmitStatement(json: EmitJson) extends Statement