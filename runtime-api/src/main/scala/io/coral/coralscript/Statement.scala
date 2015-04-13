package io.coral.coralscript

case class BreakStatement() extends Statement
case class ContinueStatement() extends Statement
case class ForStatement(decl: VariableDeclaration, mid: Expression, right: Expression, block: Statement) extends Statement
case class IfStatement(condition: TestingExpression, ifPart: Statement, elsePart: Statement) extends Statement
case class ReturnStatement(expression: Expression) extends Statement
case class StatementBlock(statements: List[Statement]) extends Statement
case class WhileStatement(condition: TestingExpression, body: Statement) extends Statement {
    println("while statement, condition: " + condition.toString + ", body: " + body.toString)
}
case class MethodDeclaration(id: Identifier, statements: StatementBlock) extends Statement
case class Assignment(id: Identifier, expression: Expression) extends Statement

abstract class Statement