package io.coral.coralscript

abstract class SelectItem
case class SelectStatement(distinct: Boolean, list: SelectList, table: TableExpression)
case class BuiltinMethod(method: String, id: Identifier) extends SelectItem
case class SelectIdentifier(i: Identifier) extends SelectItem
case class SelectList(list: Either[List[SelectItem], SelectAll]) extends SelectItem
case class FromClause(tables: TableReferenceList)
case class GroupByClause(ids: List[Identifier])
case class HavingClause()
case class OrderClause()
case class SelectAll()
case class TableAlias(i: Identifier)
case class TableExpression(from: FromClause, where: WhereClause, group: GroupByClause)
case class TableReference(id: Identifier, alias: TableAlias)
case class TableReferenceList(list: List[TableReference])
case class WhereClause(test: TestingExpression)
case class WindowClause()

case class FeatureDeclaration(id: Identifier, select: SelectStatement) extends Statement {
    override def execute() {

    }
}