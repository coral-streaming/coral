package io.coral.coralscript

case class TriggerCondition(id: Identifier, statements: ConditionBlock) extends Statement
case class TriggerAction(id: Identifier, statements: List[TriggerStatement])
case class TriggerDeclaration(action: TriggerAction, condition: TriggerCondition)
case class ConditionBlock(block: List[TriggerStatement])
case class TriggerStatement(s: Statement)