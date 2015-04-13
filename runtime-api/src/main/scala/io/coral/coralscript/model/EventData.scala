package io.coral.coralscript.model

import scala.collection.mutable.{Map => mMap}

/**
 * Represents the data of a single event.
 * In contrast to EventDefinition, this only represents
 * the data in the object, not the entire structure of the object.
 * @param id The name if the event
 * @param data The fields of the event
 */
case class EventData(id: String, data: Map[String, Any])

/**
 * Represents the data of an entity.
 * For every key, there is one entity.
 * @param id The name of the entity
 * @param key The key value of the entity
 * @param data The data of the entity
 */
case class EntityData(id: String, key: String, data: mMap[String, Any])