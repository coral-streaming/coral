package io.coral.coralscript

import scala.collection.mutable.{Map => mMap}

case class CoralScript(statements: List[Statement]) {
    // The objects as they are defined in the script
    var event_defs = mMap.empty[String, EventDeclaration]
    var entity_defs = mMap.empty[String, EntityDeclaration]
    var feature_defs = mMap.empty[String, FeatureDeclaration]
    var action_defs = mMap.empty[String, TriggerAction]
    var method_defs = mMap.empty[String, MethodDeclaration]
    var trigger_defs = mMap.empty[String, TriggerDeclaration]
    var collect_defs = mMap.empty[String, CollectDeclaration]
	var condition_defs = mMap.empty[String, TriggerCondition]

    // The actual values of the objects
    var events = mMap.empty[String, EventDeclaration]
    var entities = mMap.empty[String, EntityDeclaration]

    var alreadyParsed = false

	def prepare() {
        /**
            A CoralScript consists of the following elements:

            + Events: serve as declaration of objects to be expected.
              When they finally arrive, check the received objects against
              their event definitions. If it does not match or types cannot
              be converted, throw an exception.

            + Entities: when receiving new events, start adding the
              fields to the collected entities until now. The join conditions
              specify which field in which event belongs to which entity.

            + Features: are calculated when all information is available,
              but could also be recalculated on every new piece of information.

            + Actions: are only executed when triggered by a trigger.
              Have the same syntax as methods but must return an expression.

            + Methods: are only executed when called by an action or elsewhere.
              Have the same syntax as actions but are simply a list of statements
              and do not necessarily have to return an expression.

            + Triggers: are executed when conditions are satisfied.
              This means that when the variables mentioned in a condition
              change or new values become available, the trigger needs to
              be re-evaluated.

            + Collects: are executed when called. Collects only communicate with
              actors, this means an actor has to be implemented that knows how to
              collect the information required.

            + Separate statements: are executed immediately and in the order they
              where declared. They throw an Exception if they run into information
              which is not yet available.
         */

        // On every incoming event: check the event and add

        if (!alreadyParsed) {
            statements.foreach {
                case event: EventDeclaration =>
                    events.put(event.id.toString, event)
                case entity: EntityDeclaration =>
                    entities.put(entity.id.toString, entity)
                case feature: FeatureDeclaration =>
                    feature_defs.put(feature.id.toString, feature)
                case action: TriggerAction =>
                    action_defs.put(action.id.toString, action)
                case method: MethodDeclaration =>
                    method_defs.put(method.id.toString, method)
                case trigger: TriggerDeclaration =>
                    trigger_defs.put(trigger.action.toString, trigger)
                case collect: CollectDeclaration =>
                    collect_defs.put(collect.id.toString, collect)
				case condition: TriggerCondition =>
					condition_defs.put(condition.id.toString, condition)
                case other =>
                    // Separate statements and expressions
                    // not yet processed go here.
                    //other.execute()
            }

            alreadyParsed = true
        } else {

        }
	}
}