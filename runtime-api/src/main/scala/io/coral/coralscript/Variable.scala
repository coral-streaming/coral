package io.coral.coralscript

case class VariableDeclaration(typeSpec: String, declarator: VariableDeclarator) extends Statement {
    println("variable declaration")
}

case class VariableDeclarator(identifier: Identifier, initializer: Expression)
case class VariableInitializer()

case class Identifier(i: List[String]) extends Expression {
    println(this.getClass.toString + ": " + i.toString)

    override def toString = i.mkString(".")
}