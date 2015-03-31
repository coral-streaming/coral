package io.coral.coralscript

case class CollectDeclaration(id: Identifier, params: IdentifierList, block: CollectBlock) extends Statement
case class CollectBlock(collectFrom: CollectFrom, collectWith: CollectWith)
case class CollectFrom(name: Identifier)
case class CollectWith(info: String)