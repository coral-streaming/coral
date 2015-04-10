package io.coral.coralscript

abstract class EntityObject
case class EntityArray(id: Identifier) extends EntityObject
case class EntityCollect(call: MethodCall) extends EntityObject
case class IdentifierList(list: List[Identifier]) extends EntityObject
case class EntityBlock(block: List[EntityVariable])
case class EntityDefinition(obj: EntityObject)
case class EntityVariable(id: Identifier, definition: EntityDefinition)

case class EntityDeclaration(id: Identifier, block: EntityBlock) extends Statement {
    override def execute() {

    }

    /**
     * Retrieve the key field for a given entity. The key field
     * is the field that is used to link all other fields together.
     * For instance, if key is accountId, then all data that is looked
     * up for the entity is linked through the same accountId.
     * @return The name of the key field of the entity.
     */
    def getKey: Option[String] = {
        val key = block.block.find(v => v.id.toString == "key")

        if (key.isDefined) {
            key.get match {
                case EntityVariable(_, EntityDefinition(EventField(field))) =>
                    Some(field.toString)
                case _ =>
                    None
            }
        } else {
            None
        }
    }
}