package io.coral.coralscript

import scala.util.parsing.combinator.{JavaTokenParsers, PackratParsers}
import scala.util.parsing.input.CharSequenceReader

object CoralScriptParser extends JavaTokenParsers with PackratParsers {
    def parse(s: String): CoralScript = {
        val parseResult = phrase(script)(new PackratReader(new CharSequenceReader(s)))

        parseResult match {
            case Success(r, n) => r
            case Failure(r, n) =>
                println(parseResult)
                null
        }
    }

    type P[+T] = PackratParser[T]

    lazy val script: P[CoralScript] =
        rep(statement) ^^ { case s => CoralScript(s)}

    /** ===== STATEMENT ===== */
    lazy val statement: P[Statement] =
        (trigger_action
            | trigger_condition
            | trigger_declaration
            | variable_declaration
            | event_declaration
            | entity_declaration
            | collect_declaration
            | assignment
            | if_statement
            | while_statement
            | for_statement
            | emit_statement
            | method_declaration
            | method_call
            | feature
            | statement_block
            | "return" ~ expression ^^ { case "return" ~ e => ReturnStatement(e)}
            | "break" ^^ { case "break" => BreakStatement()}
            | "continue" ^^ { case "continue" => ContinueStatement()})
    lazy val statement_block: P[StatementBlock] =
        "{" ~ rep(statement) ~ "}" ^^ {
            case ("{" ~ s ~ "}") => StatementBlock(s)
        }
    lazy val if_statement: P[IfStatement] =
        "if" ~ "(" ~ testing_expression ~ ")" ~ statement ~ "else" ~ statement ^^ {
            case ("if" ~ "(" ~ condition ~ ")" ~ ifpart ~ "else" ~ elsepart)
            => IfStatement(condition, ifpart, elsepart)
        }
    lazy val while_statement: P[WhileStatement] =
        "while" ~ "(" ~ testing_expression ~ ")" ~ statement_block ^^ {
            case ("while" ~ "(" ~ condition ~ ")" ~ s) =>
                WhileStatement(condition, s)
        }
    lazy val for_statement: P[ForStatement] =
        "for" ~ "(" ~ variable_declaration ~ ";" ~ expression ~ ";" ~ expression ~ ")" ~ statement ^^ {
            case ("for" ~ "(" ~ declaration ~ ";" ~ middle ~ ";" ~ right ~ ";" ~ s)
            => ForStatement(declaration, middle, right, s)
        }
    lazy val method_declaration: P[MethodDeclaration] =
        identifier ~ "=" ~ statement_block ^^ {
            case i ~ "=" ~ b => MethodDeclaration(i, b)
        }
    lazy val assignment: P[Assignment] =
        identifier ~ "=" ~ expression ^^ {
            case i ~ "=" ~ expr => Assignment(i, expr)
        }

    /** ===== VARIABLE ===== */
    lazy val variable_declaration: P[VariableDeclaration] =
        type_specifier ~ variable_declarator ^^ {
            case (t ~ d) => VariableDeclaration(t, d)
        }
    lazy val variable_declarator: P[VariableDeclarator] =
        identifier ~ "=" ~ expression ^^ {
            case (id ~ "=" ~ expr) => VariableDeclarator(id, expr)
        }
    lazy val identifier: P[Identifier] =
        (repsep(ident, ".") ^^ { case i => Identifier(i)}
            | ident ^^ { case i if i.nonEmpty => Identifier(List(i))})

    /** ===== EVENT ===== */
    lazy val event_declaration: P[EventDeclaration] =
        "event" ~ identifier ~ "{" ~ event_block ~ "}" ^^ {
            case ("event" ~ id ~ "{" ~ block ~ "}") => EventDeclaration(id, block)
        }
    lazy val event_block: P[EventBlock] =
        repsep(event_variable, ",") ^^ {
            case e => EventBlock(e)
        }
    lazy val event_variable: P[EventVariable] =
        identifier ~ ":" ~ type_specifier ^^ {
            case v ~ ":" ~ t => EventVariable(v, t)
        }

    /** ===== ENTITY ===== */
    lazy val entity_declaration: P[EntityDeclaration] =
        "entity" ~ identifier ~ "{" ~ entity_block ~ "}" ^^ {
            case ("entity" ~ id ~ "{" ~ block ~ "}") => EntityDeclaration(id, block)
        }
    lazy val entity_block: P[EntityBlock] =
        rep(entity_variable) ^^ {
            case v => EntityBlock(v)
        }
    lazy val entity_variable: P[EntityVariable] =
        identifier ~ ":" ~ entity_definition ^^ {
            case (i ~ ":" ~ d) => EntityVariable(i, d)
        }
    lazy val entity_definition: P[EntityDefinition] =
        entity_object ^^ {
            case o => EntityDefinition(o)
        }
    lazy val entity_object: P[EntityObject] =
        entity_array | entity_collect | event_field
    lazy val event_field: P[EventField] =
        identifier ^^ {
            case i => EventField(i)
        }
    lazy val entity_array: P[EntityArray] =
        "Array" ~ "[" ~ identifier ~ "]" ^^ {
            case ("Array" ~ "[" ~ id ~ "]") => EntityArray(id)
        }
    lazy val entity_collect: P[EntityCollect] =
        method_call ^^ {
            case call => EntityCollect(call)
        }
    lazy val identifier_list: P[IdentifierList] =
        repsep(identifier, comma) ^^ {
            case i => i match {
                case List(Identifier(Nil)) => IdentifierList(List())
                case _ => IdentifierList(i)
            }
        }
    lazy val parameter_list: P[IdentifierList] =
        "(" ~ (identifier_list ?) ~ ")" ^^ {
            case "(" ~ l ~ ")" => l.get
        }

    /** ===== FEATURE ===== */
    lazy val feature: P[FeatureDeclaration] =
        "feature" ~ identifier ~ "{" ~ "select" ~ select_statement ~ "}" ^^ {
            case "feature" ~ id ~ "{" ~ "select" ~ stmt ~ "}" => FeatureDeclaration(id, stmt)
        }
    lazy val select_statement: P[SelectStatement] =
        select_list ~ table_expression ^^ {
            case list ~ table => SelectStatement(distinct = false, list, table)
        }
    lazy val select_list: P[SelectList] =
        (asterisk ^^ {
            case a => SelectList(Right(SelectAll()))
        }
            | repsep(select_item, comma) ^^ {
            case i => SelectList(Left(i))
        })
    lazy val select_item: P[SelectItem] =
        builtin_method | identifier ^^ {
            case i => SelectIdentifier(i)
        }
    lazy val builtin_method: P[BuiltinMethod] =
        builtin_method_name ~ "(" ~ identifier ~ ")" ^^ {
            case name ~ "(" ~ field ~ ")" => BuiltinMethod(name, field)
        }
    lazy val table_expression: P[TableExpression] =
        from_clause ~ (where_clause ?) ~ (group_by_clause ?) /*~ having_clause? ~ order_clause? ~ window_clause?*/ ^^ {
            case from ~ where ~ group => TableExpression(from, where.orNull, group.orNull)
        }
    lazy val from_clause: P[FromClause] =
        "from" ~ table_reference_list ^^ {
            case "from" ~ tables => FromClause(tables)
        }
    lazy val where_clause: P[WhereClause] =
        "where" ~ testing_expression ^^ {
            case "where" ~ test => WhereClause(test)
        }
    lazy val group_by_clause: P[GroupByClause] =
        "group" ~ "by" ~ repsep(identifier, ",") ^^ {
            case "group" ~ "by" ~ ids => GroupByClause(ids)
        }
    /*
    lazy val having_clause: P[HavingClause] =
        "having" ~
    lazy val order_clause: P[OrderClause] =
        "order" ~ "by" ~
    lazy val window_clause: P[WindowClause] =
        "window" ~
    */
    lazy val table_reference_list: P[TableReferenceList] =
        repsep(table_reference, comma) ^^ {
            case list => TableReferenceList(list)
        }
    lazy val table_reference: P[TableReference] =
        identifier ~ (as_part ?) ^^ {
            case i ~ as => TableReference(i, as.orNull)
        }
    lazy val as_part = "as" ~ table_alias ^^ {
        case "as" ~ a => a
    }
    lazy val table_alias: P[TableAlias] =
        identifier ^^ {
            case i => TableAlias(i)
        }

    /** ===== COLLECT ===== */
    lazy val collect_declaration: P[CollectDeclaration] =
        "collect" ~ identifier ~ parameter_list ~ "{" ~ collect_block ~ "}" ^^ {
            case "collect" ~ id ~ list ~ "{" ~ block ~ "}" => CollectDeclaration(id, list, block)
        }
    lazy val collect_block: P[CollectBlock] =
        collect_from ~ collect_with ^^ {
            case f ~ w => CollectBlock(f, w)
        }
    lazy val collect_from: P[CollectFrom] =
        "from" ~ ":" ~ identifier ^^ {
            case "from" ~ ":" ~ id => CollectFrom(id)
        }
    lazy val collect_with: P[CollectWith] =
        "with" ~ ":" ~ string_literal ^^ {
            case "with" ~ ":" ~ s => CollectWith(s.replace("\"", ""))
        }

    /** ===== EMIT ===== */
    lazy val emit_statement: P[EmitStatement] =
        "emit" ~ emit_json ^^ {
            case ("emit" ~ json) => EmitStatement(json)
        }
    lazy val emit_json: P[EmitJson] =
        "{" ~ repsep(emit_json_field, ",") ~ "}" ^^ {
            case ("{" ~ fields ~ "}") => EmitJson(fields)
        }
    lazy val emit_json_field: P[EmitJsonField] =
        string_literal ~ ":" ~ emit_json_value ^^ {
            case (id ~ ":" ~ expr) => EmitJsonField(Identifier(List(id.replace("\"", ""))), expr)
        }
    lazy val emit_json_value: P[EmitJsonValue] =
        (string_literal
            | boolean_literal
            | integer_literal
            | float_literal
            | identifier
            | expression
            ) ^^ {
            case value => EmitJsonValue(value)
        }

    /** ===== TRIGGER ===== */
    lazy val trigger_declaration: P[TriggerDeclaration] =
        "trigger" ~ identifier ~ "on" ~ identifier ^^ {
            case "trigger" ~ a ~ "on" ~ c => TriggerDeclaration(a, c)
        }
    lazy val trigger_action: P[TriggerAction] =
        "action" ~ identifier ~ "=" ~ "{" ~ rep(trigger_statement) ~ "}" ^^ {
            case "action" ~ id ~ "=" ~ "{" ~ statements ~ "}" =>
                TriggerAction(id, statements)
        }
    lazy val trigger_condition: P[TriggerCondition] =
        "condition" ~ identifier ~ "=" ~ condition_block ^^ {
            case "condition" ~ id ~ "=" ~ b => TriggerCondition(id, b)
        }
    lazy val condition_block: P[ConditionBlock] =
        "{" ~ rep(trigger_statement) ~ "}" ^^ {
            case "{" ~ s ~ "}" => ConditionBlock(s)
        }
    lazy val trigger_statement: P[TriggerStatement] =
        (testing_expression
            | while_statement
            | for_statement
            | if_statement
            | variable_declaration
            | emit_statement) ^^ {
            case s => TriggerStatement(s)
        }

    /** ===== EXPRESSION ===== */
    lazy val expression: P[Expression] =
        (numeric_expression
            | testing_expression
            | literal_expression
            | method_call
            | identifier
            | ("(" ~ expression ~ ")") ^^ {
            case ("(" ~ e ~ ")") => e
        })
    lazy val numeric_expression: P[NumericExpression] =
        "-" ~ numeric_expression ^^ {
            case (min ~ right) => NegExpr(right)
        } | numeric_expression ~ ("++" | "--") ^^ {
            case (left ~ incr) => IncExpr(left, incr)
        } | expression ~ ("+" | "+=" | "-" | "-=" | "*" | "*=" | "/" | "/=") ~ expression ^^ {
            case (left ~ op ~ right) => StandardNumExpr(left, op, right)
        }
    lazy val testing_expression: P[TestingExpression] =
        expression ~ (">" | "<" | ">=" | "<=" | "==" | "!=") ~ expression ^^ {
            case (left ~ test ~ right) => TestingExpression(left, test, right)
        } | expression ~ ("&&" | "||") ~ expression ^^ {
            case (left ~ op ~ right) => TestingExpression(left, op, right) }
    lazy val literal_expression: P[LiteralExpression] =
        float_literal ^^ {
            case f => FloatLitExpr(f.toFloat)
        } | integer_literal ^^ {
            case i => IntLitExpr(i.toInt)
        } | stringLiteral ^^ {
            case s => StrLitExpr(s)
        }
    lazy val method_call: P[MethodCall] =
        identifier ~ parameter_list ^^ {
            case i ~ list => MethodCall(i, list)
        }

    /** ===== LITERALS ===== */
    lazy val integer_literal = wholeNumber
    lazy val float_literal = """-?\d+\.\d+""".r
    lazy val string_literal = stringLiteral
    lazy val boolean_literal = "true" | "false"
    lazy val type_specifier =
        ("boolean"
            | "int"
            | "float"
            | "long"
            | "string"
            | "datetime")
    lazy val builtin_method_name =
        ("avg"
            | "max"
            | "min"
            | "sum"
            | "count")
    lazy val comma = ","
    lazy val distinct = "distinct"
    lazy val asterisk = "*"
}