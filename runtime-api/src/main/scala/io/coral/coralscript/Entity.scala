package io.coral.coralscript

abstract class EntityObject
case class EntityArray(id: Identifier) extends EntityObject
case class EntityCollect(id: Identifier, list: IdentifierList) extends EntityObject
case class IdentifierList(list: List[Identifier]) extends EntityObject
case class EntityBlock(block: List[EntityVariable])
case class EntityDefinition(obj: EntityObject)
case class EntityVariable(id: Identifier, definition: EntityDefinition)

case class EntityDeclaration(id: Identifier, block: EntityBlock) extends Statement {
    override def execute() {

    }
}