package io.coral.coralscript

case class EventBlock(block: List[EventVariable])
case class EventField(id: Identifier) extends EntityObject
case class EventVariable(id: Identifier, typeSpec: String)

case class EventDeclaration(id: Identifier, block: EventBlock) extends Statement {
    override def execute() {

    }
}