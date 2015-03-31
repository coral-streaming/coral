package io.coral.coralscript

case class JsonIdentifier(names: List[Identifier]) extends Identifier(names.mkString("."))
case class OutVariable(name: String) extends Identifier(name)
case class SimpleIdentifier(id: String) extends Identifier(id)
case class VariableDeclaration(typeSpec: String, declarator: VariableDeclarator) extends Statement
case class VariableDeclarator(identifier: Identifier, initializer: Expression)
case class VariableInitializer()

abstract class Identifier(i: String) extends Expression {
    override def toString = i
}