package io.coral.coralscript

abstract class LiteralExpression extends Expression
abstract class NumericExpression extends Expression
case class AndExpression(left: Expression, right: Expression) extends Expression
case class FloatLiteralExpression(f: Float) extends LiteralExpression
case class IncrementExpression(expression: Expression, increment: String) extends NumericExpression
case class IntegerLiteralExpression(i: Int) extends LiteralExpression
case class NegationExpression(expression: Expression) extends NumericExpression
case class NumericLiteralExpression() extends Expression
case class OrExpression(left: Expression, right: Expression) extends Expression
case class OutExpression(declaration: VariableDeclaration) extends Expression
case class StandardNumericExpression(left: Expression, op: String, right: Expression) extends NumericExpression
case class StringLiteralExpression(s: String) extends LiteralExpression
case class TestingExpression(left: Expression, test: String, right: Expression) extends Expression

abstract class Expression extends Statement {
    override def execute() {

    }
}