package io.coral.coralscript

abstract class LiteralExpression[T] extends Expression[T]

case class FloatLitExpr(f: Float) extends LiteralExpression[FloatLitExpr] {
	override val evaluate = f
	def gt(o: FloatLitExpr) = f > o.f
	def ste(o: FloatLitExpr) = f <= o.f
	def ne(o: FloatLitExpr) = f != o.f
	def gte(o: FloatLitExpr) = f >= o.f
	def st(o: FloatLitExpr) = f < o.f
	def e(o: FloatLitExpr) = f == o.f
	def plus(o: FloatLitExpr) = f + o.f
	def minus(o: FloatLitExpr) = f - o.f
	def times(o: FloatLitExpr) = f * o.f
	def divide(o: FloatLitExpr) = f / o.f
	def or(o: FloatLitExpr) = throw new IllegalArgumentException("ste")
	def and(o: FloatLitExpr) = throw new IllegalArgumentException("ste")
}

case class IntLitExpr(i: Int) extends LiteralExpression[IntLitExpr] {
	override val evaluate = i

	def gt(o: IntLitExpr) = i > o.i
	def ste(o: IntLitExpr) = i <= o.i
	def ne(o: IntLitExpr) = i != o.i
	def gte(o: IntLitExpr) = i >= o.i
	def st(o: IntLitExpr) = i < o.i
	def e(o: IntLitExpr) = i == o.i
	def plus(o: IntLitExpr) = i + o.i
	def minus(o: IntLitExpr) = i - o.i
	def times(o: IntLitExpr) = i * o.i
	def divide(o: IntLitExpr) = i / o.i
	def or(o: IntLitExpr) = throw new IllegalArgumentException("ste")
	def and(o: IntLitExpr) = throw new IllegalArgumentException("ste")
}

case class StrLitExpr(s: String) extends LiteralExpression[StrLitExpr] {
	override val evaluate = s
	def gt(o: StrLitExpr) = throw new IllegalArgumentException("gt")
	def ste(o: StrLitExpr) = throw new IllegalArgumentException("ste")
	def or(o: StrLitExpr) = throw new IllegalArgumentException("or")
	def and(o: StrLitExpr) = throw new IllegalArgumentException("and")
	def ne(o: StrLitExpr) = throw new IllegalArgumentException("ne")
	def gte(o: StrLitExpr) = throw new IllegalArgumentException("gte")
	def st(o: StrLitExpr) = throw new IllegalArgumentException("st")
	def e(o: StrLitExpr) = throw new IllegalArgumentException("e")
	def plus(o: StrLitExpr) = throw new IllegalArgumentException("plus")
	def minus(o: StrLitExpr) = throw new IllegalArgumentException("minus")
	def times(o: StrLitExpr) = throw new IllegalArgumentException("times")
	def divide(o: StrLitExpr) = throw new IllegalArgumentException("divide")
}

abstract class NumericExpression[T] extends Expression[T]

case class StandardNumExpr(left: Expression, op: String, right: Expression) extends NumericExpression[StandardNumExpr] {
	override val evaluate = {
		op match {
			case "+" => left.plus(right)
			case "-" => left.minus(right)
			case "*" => left.times(right)
			case "/" => left.divide(right)
			case other => throw new IllegalArgumentException(other)
		}
	}
}

case class IncExpr(expression: NumericExpression, increment: String) extends NumericExpression {
	override val evaluate = {
		increment match {
			case "++" => expression.plus(IntLitExpr(1))
			case "--" => expression.minus(IntLitExpr(1))
			case other => throw new IllegalArgumentException(other)
		}
	}
}

case class NegExpr(expression: Expression) extends NumericExpression {
	override val evaluate = expression.times(IntLitExpr(-1))
}

case class AndExpression(left: Expression, right: Expression) extends Expression {
	override val evaluate = left.and(right)
}

case class OrExpression(left: Expression, right: Expression) extends Expression {
	override val evaluate = left.or(right)
}

case class MethodCall(id: Identifier, list: IdentifierList) extends Expression {
	override val evaluate = {

	}
}

case class TestingExpression(left: Expression, test: String, right: Expression) extends Expression {
    println("testing expression, left: " + left.toString + ", test: " + test + ", right: " + right.toString)

	override val evaluate = {
		test match {
			case "<" => left.st(right)
			case "<=" => left.ste(right)
			case ">" => left.gt(right)
			case ">=" => left.gte(right)
			case "!=" => left.ne(right)
			case "==" => left.e(right)
			case "&&" => left.and(right)
			case "||" => left.or(right)
			case other => throw new IllegalArgumentException(other)
		}
	}
}

abstract class Expression[T <: Expression] extends Statement {
	println("Evaluating expression")

	def evaluate: Any

	def gt(o: T)// = throw new IllegalArgumentException("gt")
	def ste(o: T)// = throw new IllegalArgumentException("ste")
	def or(o: T)// = throw new IllegalArgumentException("or")
	def and(o: T)// = throw new IllegalArgumentException("and")
	def ne(o: T)// = throw new IllegalArgumentException("ne")
	def gte(o: T)// = throw new IllegalArgumentException("gte")
	def st(o: T)// = throw new IllegalArgumentException("st")
	def e(o: T)// = throw new IllegalArgumentException("e")
	def plus(o: T)// = throw new IllegalArgumentException("plus")
	def minus(o: T)// = throw new IllegalArgumentException("minus")
	def times(o: T)// = throw new IllegalArgumentException("times")
	def divide(o: T)// = throw new IllegalArgumentException("divide")
}