package io.coral.coralscript

import org.scalatest.FunSuite

import scala.util.parsing.combinator.PackratParsers
import scala.util.parsing.input.CharSequenceReader

class TestStatements extends FunSuite with PackratParsers {
	test("single assignment") {
		val script = "int a = 1\n\n"
		val result = CoralScriptParser.parse(script)
		assert(result == CoralScript(List(VariableDeclaration("int",
			VariableDeclarator(SimpleIdentifier("a"), IntegerLiteralExpression(1))))))
	}

	test("multiple assignments") {
		val script = "float a = 323.12\nint x = 10"

		val result = CoralScriptParser.parse(script)
		assert(result == CoralScript(List(VariableDeclaration("float",
			VariableDeclarator(SimpleIdentifier("a"), FloatLiteralExpression(323.12f))),
			VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("x"),
				IntegerLiteralExpression(10))))))
	}

	test("script1") {
		val script = "int x = 10\n" +
			"int y = 12\n" +
			"int z = x + y * 2\n" +
			"out int bla = z + 3\n"

		val result = CoralScriptParser.parse(script)
		assert(result == CoralScript(List(
			VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("x"), IntegerLiteralExpression(10))),
			VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("y"), IntegerLiteralExpression(12))),
			VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("z"), StandardNumericExpression(SimpleIdentifier("x"),
				"+", StandardNumericExpression(SimpleIdentifier("y"), "*", IntegerLiteralExpression(2))))),
			OutExpression(VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("bla"),
				StandardNumericExpression(SimpleIdentifier("z"), "+", IntegerLiteralExpression(3))))))))
	}

	test("simple assignments") {
		val script1 = "int x = 10\n"
		val result1 = parse(CoralScriptParser.variable_declaration, script1)
		assert(result1 == VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("x"),
			IntegerLiteralExpression(10))))

		val script2 = "int y = 20" // without \n
		val result2 = parse(CoralScriptParser.variable_declaration, script2)
		assert(result2 == VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("y"),
			IntegerLiteralExpression(20))))

		val script3 = "int z = 10 + 20\n"
		val result3 = parse(CoralScriptParser.variable_declaration, script3)
		assert(result3 == VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("z"),
			StandardNumericExpression(IntegerLiteralExpression(10),
				"+", IntegerLiteralExpression(20)))))
	}

	test("ifstatement1") {
		val script =
			"if (x == 10) {\n" +
			"   int y = 20\n" +
			"   int z = y + 5\n" +
			"} else {\n" +
			"   int b = 12\n" +
			"}"
		val result = parse(CoralScriptParser.if_statement, script)
		assert(result == IfStatement(TestingExpression(SimpleIdentifier("x"), "==", IntegerLiteralExpression(10)),
			StatementBlock(List(VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("y"), IntegerLiteralExpression(20))),
			VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("z"), StandardNumericExpression(SimpleIdentifier("y"), "+",
				IntegerLiteralExpression(5)))))),
			StatementBlock(List(VariableDeclaration("int", VariableDeclarator(SimpleIdentifier("b"),
				IntegerLiteralExpression(12)))))))
	}

	def parse[T <: Statement](subParser: CoralScriptParser.Parser[T], script: String): T = {
		CoralScriptParser.phrase(subParser)(new PackratReader(new CharSequenceReader(script))).get
	}
}