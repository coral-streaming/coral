package io.coral.coralscript

import org.scalatest.FunSuite

import scala.util.parsing.combinator.PackratParsers
import scala.util.parsing.input.CharSequenceReader

class CoralScriptStatementsSpec extends FunSuite with PackratParsers {
	test("single correct assignment to int") {
		val script = "int a = 1\n\n"
		val result = CoralScriptParser.parse(script)
		assert(result == CoralScript(List(VariableDeclaration("int",
			VariableDeclarator(Identifier(List("a")), IntLitExpr(1))))))
	}

	test("multiple correct assignments") {
		val script = "float a = 323.12\nint x = 10"

		val result = CoralScriptParser.parse(script)
		assert(result == CoralScript(List(VariableDeclaration("float",
			VariableDeclarator(Identifier(List("a")), FloatLitExpr(323.12f))),
			VariableDeclaration("int", VariableDeclarator(Identifier(List("x")),
				IntLitExpr(10))))))
	}

	test("script1") {
		val script = "int x = 10\n" +
			"int y = 12\n" +
			"int z = x + y * 2\n" +
			"int bla = z + 3\n"

		val result = CoralScriptParser.parse(script)
		assert(result == CoralScript(List(
			VariableDeclaration("int", VariableDeclarator(Identifier(List("x")), IntLitExpr(10))),
			VariableDeclaration("int", VariableDeclarator(Identifier(List("y")), IntLitExpr(12))),
			VariableDeclaration("int", VariableDeclarator(Identifier(List("z")), StandardNumExpr(Identifier(List("x")),
				"+", StandardNumExpr(Identifier(List("y")), "*", IntLitExpr(2))))),
            VariableDeclaration("int",VariableDeclarator(Identifier(List("bla")),
                StandardNumExpr(Identifier(List("z")), "+", IntLitExpr(3)))))))
	}

	test("simple assignments") {
		val script1 = "int x = 10\n"
		val result1 = parse(CoralScriptParser.variable_declaration, script1)
		assert(result1 == VariableDeclaration("int", VariableDeclarator(Identifier(List("x")),
			IntLitExpr(10))))

		val script2 = "int y = 20" // without \n
		val result2 = parse(CoralScriptParser.variable_declaration, script2)
		assert(result2 == VariableDeclaration("int", VariableDeclarator(Identifier(List("y")),
			IntLitExpr(20))))

		val script3 = "int z = 10 + 20\n"
		val result3 = parse(CoralScriptParser.variable_declaration, script3)
		assert(result3 == VariableDeclaration("int", VariableDeclarator(Identifier(List("z")),
			StandardNumExpr(IntLitExpr(10),
				"+", IntLitExpr(20)))))
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
		assert(result == IfStatement(TestingExpression(Identifier(List("x")), "==", IntLitExpr(10)),
			StatementBlock(List(VariableDeclaration("int", VariableDeclarator(Identifier(List("y")), IntLitExpr(20))),
			VariableDeclaration("int", VariableDeclarator(Identifier(List("z")), StandardNumExpr(Identifier(List("y")), "+",
				IntLitExpr(5)))))),
			StatementBlock(List(VariableDeclaration("int", VariableDeclarator(Identifier(List("b")),
				IntLitExpr(12)))))))
	}

    test("if statement nested while") {
        val script =
            "if (x == 10) {\n" +
                "   while (y < 20) {\n" +
                "      int z = y + 5\n" +
                "   }\n" +
                "} else {\n" +
                "   int b = 12\n" +
                "}"
        val result = parse(CoralScriptParser.if_statement, script)

        assert(result == IfStatement(TestingExpression(Identifier(List("x")),"==", IntLitExpr(10)),
            StatementBlock(List(WhileStatement(TestingExpression(Identifier(List("y")),"<",IntLitExpr(20)),
                StatementBlock(List(VariableDeclaration("int",VariableDeclarator(Identifier(List("z")),
                    StandardNumExpr(Identifier(List("y")),"+",IntLitExpr(5))))))))),
            StatementBlock(List(VariableDeclaration("int", VariableDeclarator(Identifier(List("b")),
                IntLitExpr(12)))))))
    }

	test("expression1") {
		val script = "10"
		val result = parse(CoralScriptParser.expression, script)
		assert(result == IntLitExpr(10))

		val answer = result.evaluate
		assert(answer == 10)
	}

	test("expression2") {
		val script = "10 > 20 && 5 < 1"
		val result = parse(CoralScriptParser.expression, script)
		val returnValue = result.evaluate
		assert(returnValue == false)
	}

	test("expression3") {
		val script = "10 < 20 || 5 > 10"
		val result = parse(CoralScriptParser.expression, script)
		val returnValue = result.evaluate
		assert(returnValue == true)
	}

    test("event1") {
        val event = """event Transaction {
                transactionId: long,
                accountId: long,
                amount: float,
                datetime: datetime,
                description: string
            }"""

        val result = parse(CoralScriptParser.event_declaration, event)
        assert(result == EventDeclaration(Identifier(List("Transaction")), EventBlock(List(
            EventVariable(Identifier(List("transactionId")), "long"),
            EventVariable(Identifier(List("accountId")), "long"),
            EventVariable(Identifier(List("amount")), "float"),
            EventVariable(Identifier(List("datetime")), "datetime"),
            EventVariable(Identifier(List("description")), "string")))))
    }

    test("incorrect event") {
        val event = """event Transaction {
                transactionId: long,
                accountId: long,
                amount: float,
                datetime: datetime,
                description:
            }"""

        intercept[java.lang.RuntimeException] {
            parse(CoralScriptParser.event_declaration, event)
        }
    }

    test("empty event") {
        val event = """event Transaction {}"""
        val result = parse(CoralScriptParser.event_declaration, event)
        assert(result == EventDeclaration(Identifier(List("Transaction")), EventBlock(List())))
    }

    test("entity1") {
        val entity =
            """entity Person {
               key: accountId
               age: collectAge(accountId)
               transactions: Array[Transaction]
               currentBalance: BalanceInfo.amount
            }"""

        val result = parse(CoralScriptParser.entity_declaration, entity)
        assert(result == EntityDeclaration(Identifier(List("Person")),
            EntityBlock(List(
                EntityVariable(Identifier(List("key")),
                    EntityDefinition(EventField(Identifier(List("accountId"))))),
                EntityVariable(Identifier(List("age")),EntityDefinition(EntityCollect(
                    MethodCall(Identifier(List("collectAge")), IdentifierList(List(Identifier(List("accountId")))))))),
                EntityVariable(Identifier(List("transactions")),
                    EntityDefinition(EntityArray(Identifier(List("Transaction"))))),
                EntityVariable(Identifier(List("currentBalance")),
                    EntityDefinition(EventField(Identifier(List("BalanceInfo","amount")))))))))
    }

    test("entity2") {
        val entity =
            """entity Person {
               field: collectSomething(accountId)
            }"""

        val result = parse(CoralScriptParser.entity_declaration, entity)
        assert(result == EntityDeclaration(Identifier(List("Person")),
            EntityBlock(List(
                EntityVariable(Identifier(List("field")),
                    EntityDefinition(EntityCollect(MethodCall(Identifier(List("collectSomething")),
                        IdentifierList(List(Identifier(List("accountId"))))))))))))
    }

    test("entity3") {
        val entity =
            """entity Person {
               field: Array[Transaction]
            }"""

        val result = parse(CoralScriptParser.entity_declaration, entity)
        assert(result == EntityDeclaration(Identifier(List("Person")),
            EntityBlock(List(
                EntityVariable(Identifier(List("field")),
                    EntityDefinition(EntityArray(Identifier(List("Transaction")))))))))
    }

    test("entity4") {
        val entity =
            """entity Person {
               field: Array[BalanceInfo.amount]
            }"""

        val result = parse(CoralScriptParser.entity_declaration, entity)
        assert(result == EntityDeclaration(Identifier(List("Person")),
            EntityBlock(List(
                EntityVariable(Identifier(List("field")),
                    EntityDefinition(EntityArray(Identifier(List("BalanceInfo", "amount")))))))))
    }

    test("entity5") {
        val entity =
            """entity Person {
               field: BalanceInfo.amount
            }"""

        val result = parse(CoralScriptParser.entity_declaration, entity)
        assert(result == EntityDeclaration(Identifier(List("Person")),
            EntityBlock(List(
                EntityVariable(Identifier(List("field")),
                    EntityDefinition(EntityArray(Identifier(List("BalanceInfo", "amount")))))))))
    }

    test("collect1") {
        val collect =
            """collect collectAge(accountId) {
                from: db1Actor
                with: "select age from customers where accountId = {accountId}"
            }"""

        val result = parse(CoralScriptParser.collect_declaration, collect)
        val expected = CollectDeclaration(
            Identifier(List("collectAge")), IdentifierList(List(Identifier(List("accountId")))),
            CollectBlock(CollectFrom(Identifier(List("db1Actor"))),
                CollectWith("select age from customers where accountId = {accountId}")))
        assert(result == expected)
    }

    test("collect2") {
        val collect =
            """collect collectAge() {
                from: otherActor
                with: "select something from someTable"
            }"""

        val result = parse(CoralScriptParser.collect_declaration, collect)
        val expected = CollectDeclaration(
            Identifier(List("collectAge")), IdentifierList(List()),
            CollectBlock(CollectFrom(Identifier(List("otherActor"))),
                CollectWith("select something from someTable")))
        assert(result == expected)
    }

    test("feature1") {
        val feature =
            """feature avgAmountPerDay {
              select avg(Person.transactions.amount)
              from Person
              group by day
            }"""

        val result = parse(CoralScriptParser.feature, feature)
        val expected = FeatureDeclaration(Identifier(List("avgAmountPerDay")),
            SelectStatement(false, SelectList(Left(List(BuiltinMethod("avg",
                Identifier(List("Person","transactions","amount")))))),
                TableExpression(FromClause(TableReferenceList(List(
                    TableReference(Identifier(List("Person")), null)))),
                    null, GroupByClause(List(Identifier(List("day")))))))
        assert(result == expected)
    }

    test("action1") {
        val action =
            """action action1 = {
                   emit { "transactionId": Transaction.transactionId, "outlier": true }
            }"""

        val result = parse(CoralScriptParser.trigger_action, action)
        val expected = TriggerAction(Identifier(List("action1")),
            List(TriggerStatement(EmitStatement(
                EmitJson(List(EmitJsonField(Identifier(List("transactionId")),
                    EmitJsonValue(Identifier(List("Transaction","transactionId")))),
                    EmitJsonField(Identifier(List("outlier")),EmitJsonValue("true"))))))))

        assert(result == expected)
    }

    test("condition1") {
        val condition =
            """condition condition1 = {
                   Transaction.amount > max(avgAmountPerDay)
            }"""

        val result = parse(CoralScriptParser.trigger_condition, condition)
        val expected =
            TriggerCondition(Identifier(List("condition1")),
                ConditionBlock(List(TriggerStatement(TestingExpression(Identifier(List("Transaction", "amount")),
                    ">", MethodCall(Identifier(List("max")),
                        IdentifierList(List(Identifier(List("avgAmountPerDay"))))))))))
        assert(result == expected)
    }

    test("condition2") {
        val condition =
            """condition condition1 = {
                   Transaction.amount > min(avgAmountPerDay) && Transaction.amount < max(avgAmountPerDay)
            }"""

        val result = parse(CoralScriptParser.trigger_condition, condition)
        val expected =
            TriggerCondition(Identifier(List("condition1")),
                ConditionBlock(List(TriggerStatement(TestingExpression(Identifier(List("Transaction", "amount")), ">",
                    TestingExpression(MethodCall(Identifier(List("min")),
                    IdentifierList(List(Identifier(List("avgAmountPerDay"))))), "&&",
                    TestingExpression(Identifier(List("Transaction", "amount")), "<",
                    MethodCall(Identifier(List("max")),
                    IdentifierList(List(Identifier(List("avgAmountPerDay"))))))))))))

        assert(result == expected)
    }

    test("trigger1") {
        val trigger = """trigger action1 on condition1"""
        val result = parse(CoralScriptParser.trigger_declaration, trigger)
        val expected = TriggerDeclaration(Identifier(List("action1")), Identifier(List("condition1")))
        assert(result == expected)
    }

	def parse[T <: Statement](subParser: CoralScriptParser.Parser[T], script: String): T = {
		CoralScriptParser.phrase(subParser)(new PackratReader(new CharSequenceReader(script))).get
	}
}