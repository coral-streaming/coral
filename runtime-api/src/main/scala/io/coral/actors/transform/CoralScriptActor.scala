package io.coral.actors.transform

import akka.actor.Props
import io.coral.actors.CoralActor
import io.coral.coralscript._
import io.coral.coralscript.model.{EntityData, EventData}
import org.joda.time.DateTime
import org.json4s.JsonAST.{JValue, JObject}
import org.json4s._
import scala.collection.mutable.{Map => mMap, ListBuffer}
import scala.concurrent.Future
import scala.util.matching.Regex.Match
import scalaz.OptionT

object CoralScriptActor {
    implicit val formats = org.json4s.DefaultFormats

    def getParams(json: JValue) = {
        for {
            script <- (json \ "params" \ "script").extractOpt[String]
        } yield {
            script
        }
    }

    def apply(json: JValue): Option[Props] = {
        getParams(json).map(_ => Props(classOf[CoralScriptActor], json))
    }
}

class CoralScriptActor(json: JObject) extends CoralActor {
    def jsonDef = json
    def state = Map()

    val scriptString = CoralScriptActor.getParams(json).get
    var script: CoralScript = _
    // A list with all current entities
    var entities = mMap.empty[String, EntityData]
    // A list with all previous events
    var events = mMap.empty[String, ListBuffer[EventData]]

    override def preStart() {
        script = CoralScriptParser.parse(scriptString)
        script.prepare()
    }

    def emit = doNotEmit
    def timer = notSet

    def trigger = {
        json: JObject =>
            /**
             * The processing flow when a new event comes in is as follows:
             *    1) Match incoming objects against defined events and check fields
             *    2) Add the fields of the event to any defined entities, if necessary
             *    3) Execute any collect statements that need to be re-executed
             *    4) Recalculate any features if necessary
             *    5) Recalculate all changed conditions
             *    6) Find out which triggers are triggered because of updated conditions
             */
            val event: EventData = matchEvent(json)
            addToEvents(event)
            updateEntities(event)
            recalculateFeatures()
            val triggeredTriggers = findTriggeredTriggers()
            executeActions(triggeredTriggers)

            OptionT.some(Future.successful({}))
    }

    /**
     * Find out to which event a json object belongs. If no object
     * is found, throw an exception. Returns a new object EventData
     * with a map with all values in it.
     * @param json The json object to find the event declaration for.
     * @return An EventDeclaration if a matching event is found.
     *         When no matching EventDeclaration is found, an
     *         IllegalArgumentException is thrown.
     */
    def matchEvent(json: JObject): EventData = {
        // We assume that an event matches if all fields required
        // are present in the JSON at root level and that all
        // values can be converted to their definitions in the event.
        val JString(typeName) = json \ "datatype"

        script.events(typeName) match {
            case EventDeclaration(_, block) =>
                // Look up each variable with definition
                val result = new EventData(typeName, block.block.map(variable => {
                    val id = variable.id.toString
                    val typeSpec = variable.typeSpec

                    val value = json \ id match {
                        // When the variable is not found in
                        // the trigger json, something is wrong
                        case JNull =>
                            throw new IllegalArgumentException(id.toString)
                        case valid =>
                            typeSpec match {
                                case "boolean" => valid.extract[Boolean]
                                case "int" => valid.extract[Int]
                                case "float" => valid.extract[Float]
                                case "long" => valid.extract[Long]
                                case "string" => valid.extract[String]
                                case "datetime" => extractDateTime(valid)
                                case _ => throw new IllegalArgumentException(typeSpec)
                            }
                    }

                    (id, value)
                }).toMap[String, Any])

                result
            case null =>
                throw new IllegalArgumentException(typeName)
        }
    }

    def extractDateTime(valid: JValue): DateTime =  {
        null
    }

    /**
     * Adds an event to the map of events.
     * @param event The event to add
     */
    def addToEvents(event: EventData) {
        // Verbose.
        if (events.contains(event.id)) {
            events.get(event.id).get += event
        } else {
            val listbuffer = ListBuffer.empty[EventData]
            listbuffer += event
        }
    }

    /**
     * Update any entity that uses information from this event
     * with the new data. If no entities use information from
     * this event, do nothing.
     * @param event The event to use to update the entities.
     */
    def updateEntities(event: EventData) {
        // Find all entities that uses information from this event.
        // For each of these entities, find out if it is:
        //    1) Using a value from an event directly, in this case: just fill it in;
        //    2) Collects values of this event in an array, in this case
        //       - Find the entity with the given join key equal to the key of the event
        //       - Add the item to the array of the entity with the right join key
        //    3) Uses a collect definition to another actor, in this case:
        //       ask the actor for the information
        script.entities.foreach(entity => {
            // This is the key that links data to this instance
            val key = entity._2.getKey
            key match { case None => throw new IllegalArgumentException("missing key"); case _ => }

            // Iterate over all variables/fields in the entity
            val instance = entities.getOrElseUpdate(entity._1,
                EntityData(entity._1, key.get, mMap.empty[String, Any]))

            entity._2.block.block.foreach(v => {
                // These are the only 3 possibilities
                v.definition match {
                    case EntityDefinition(EntityArray(id)) =>
                        // Add the item to the array, if applicable
                        if (event.id == id.toString) {
                            instance.data.put(v.id.toString, event)
                        }
                    case EntityDefinition(EntityCollect(call)) =>
                        val collectedData = collectData(call)
                        instance.data.put(v.id.toString, collectedData)
                    case EntityDefinition(EventField(id)) if v.id.toString != "key" =>
                        // In the case of a simple event field, always fill in the latest one
                        // id.list(0) is the base object of the field
                        if (event.id == id.i(0)) {
                            val latest: Option[ListBuffer[EventData]] = events.get(event.id)
                            if (latest.isDefined) {
                                val key = ""
                                val value = latest.get.head.data(key)
                                // Update the instance with this data
                                instance.data.put(key, value)
                            }
                        }
                    case _ =>
                        println("Can not process entity update with event")
                }
            })
        })
    }

    /**
     * Collects data with a given collect method definition.
     * @param call The method to use to collect the data
     * @return The object that was collected
     */
    def collectData(call: MethodCall): Any = {
        val name = call.id
        val params = call.list

        val collectProc: CollectDeclaration =
			script.collect_defs.getOrElse(name.toString, null)

        if (collectProc == null) {
            null
        } else {
			// The name of the actor to get the data from
			val fromActorName = collectProc.block.collectFrom.name.toString
			// The query to execute to get the data
			var collectWith = collectProc.block.collectWith.info
			// Replace any parameters in the query string

			collectWith = replaceParams(collectWith, params)
        }
    }

	/**
	 * Replace a parameter denoted with {param} with the actual
	 * value of the parameter.
	 * @param collectWith The collectWith definition with parameter fields in it
	 * @param params The list of actual parameter values
	 * @return The original string but with parameter values replaced.
	 */
	def replaceParams(collectWith: String, params: IdentifierList): String = {
		val regex = """\{(.+)\}"""".r
		val matchResult = regex.findAllMatchIn(collectWith)

		matchResult.foreach(m => {
			val paramName = m.group(1)
		})

		null
	}

    /**
     * Recalculate features based on incoming information.
     * This method only recalculates features that are actually changed
     * by the new information so it needs to find these first.
     */
    def recalculateFeatures() {

    }

    /**
     * Recalculate all conditions that are changed because of the new data.
     * Find out out which of these conditions are true and which are false.
     * If a condition is true, it will trigger all triggers that use that condition.
     * If a condition is false, the triggers will not be triggered.
     * @return A list of all conditions that evaluate to true.
     */
    def findTriggeredTriggers(): List[TriggerDeclaration] = {
		val result = ListBuffer.empty[TriggerDeclaration]

    	script.trigger_defs.foreach(t => {
			val triggerName = t._1
			val triggerDefinition = t._2
			val conditionName = triggerDefinition.condition.toString
			val condition = script.condition_defs.getOrElse(conditionName, null)

			if (condition != null) {
				// Can there be multiple expressions in the block?
				condition.statements.block.foreach(s => {
					// It should be an expression so it returns a value
					//val returnValue = s.s.execute().asInstanceOf[Boolean]

					//if (returnValue == true) {
					//	// We have a condition that evaluates to true
					//	result += t._2
					//}
				})
			}
		})

		result.toList
    }

    /**
     * Execute the actions that belong to the list of triggers that are
     * triggered. Multiple actions can belong to the same trigger so
     * make sure to execute all of them.
     * @param triggeredTriggers The list of triggers that are triggered.
     */
    def executeActions(triggeredTriggers: List[TriggerDeclaration]) {
        triggeredTriggers.foreach(t => {
            //t.action.execute()
        })
    }
}