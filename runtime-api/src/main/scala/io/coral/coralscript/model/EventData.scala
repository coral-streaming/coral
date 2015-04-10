package io.coral.coralscript.model

import scala.collection.mutable.{Map => mMap}

case class EventData(id: String, data: Map[String, Any])
case class EntityData(id: String, key: String, data: mMap[String, Any])