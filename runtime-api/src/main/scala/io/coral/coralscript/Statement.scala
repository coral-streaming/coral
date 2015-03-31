package io.coral.coralscript

case class BreakStatement() extends Statement
case class ContinueStatement() extends Statement
case class ForStatement(decl: VariableDeclaration, mid: Expression, right: Expression, block: Statement) extends Statement
case class IfStatement(condition: TestingExpression, ifPart: Statement, elsePart: Statement) extends Statement
case class ReturnStatement(expression: Expression) extends Statement
case class StatementBlock(statements: List[Statement]) extends Statement
case class WhileStatement(condition: TestingExpression, body: Statement) extends Statement
case class MethodDeclaration(id: Identifier, statements: StatementBlock) extends Statement

abstract class Statement {
    def execute() {

    }
}