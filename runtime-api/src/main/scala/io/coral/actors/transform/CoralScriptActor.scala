package io.coral.actors.transform

import akka.actor.Props
import io.coral.actors.CoralActor
import io.coral.coralscript._
import org.json4s.JValue
import org.json4s.JsonAST.{JValue, JObject}
import org.json4s._

import scala.concurrent.Future
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

    override def preStart() {
        script = CoralScriptParser.parse(scriptString)
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
            val event: EventDeclaration = matchEvent(json)
            updateEntities(event)
            recalculateFeatures()
            val changedConditions = recalculateConditions()
            val triggeredTriggers = findTriggeredTriggers(changedConditions)
            executeActions(triggeredTriggers)

            OptionT.some(Future.successful({}))
    }

    /**
     * Find out to which event a json object belongs. If no object
     * is found, throw an exception.
     * @param json The json object to find the event declaration for.
     * @return An EventDeclaration if a matching event is found.
     *         When no matching EventDeclaration is found, an
     *         IllegalArgumentException is thrown.
     */
    def matchEvent(json: JObject): EventDeclaration = {
        // We assume that an event matches if all fields required
        // are present in the JSON at root level and that all
        // values can be converted to their definitions in the event.
        val typeName = (json \ "datatype").toString

        script.event_defs(typeName) match {
            case EventDeclaration(_, block) =>
                block.block.foreach(variable => {
                    val id = variable.id.toString
                    val typeSpec = variable.typeSpec

                    json \ id match {
                        // When the variable is not found in
                        // the trigger json, something is wrong
                        case JNull =>
                            throw new IllegalArgumentException(id.toString)
                        case valid =>
                            val value = typeSpec match {
                                case "Boolean" => false // valid.getAs[Boolean]
                                case "Int" => 0 // valid.getAs[Int]
                                case "Float" => 0.0f //valid.getAs[Float]
                                case "Long" => 0L // valid.getAs[Long]
                                case "String" => "" //valid.getAs[String]
                                case "DateTime" => // getDateTime(valid)
                                case _ => throw new IllegalArgumentException(typeSpec)
                            }
                    }
                })

                // Here all variables are present and are of the
                // right type that is defined in the event.
                null
            case null =>
                throw new IllegalArgumentException(typeName)
        }
    }

    /**
     * Update any entity that uses information from this event
     * with the new data. If no entities use information from
     * this event, do nothing.
     * @param event The event to use to update the entities.
     */
    def updateEntities(event: EventDeclaration) {
        // Find all entities that uses information from this event.
        // For each of these entities, find out if it is:
        //    1) Using a value from an event directly, in this case: just fill it in;
        //    2) Collects values of this event in an array, in this case
        //       - Find the entity with the given join key equal to the key of the event
        //       - Add the item to the array of the entity with the right join key
        //    3) Uses a collect definition to another actor, in this case:
        //       ask the actor for the information

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
    def recalculateConditions(): List[TriggerCondition] = {
        List()
    }

    /**
     * Based on the list of conditions that are sure to evaluate to true,
     * give a list of triggers that need to be triggered because of the
     * changed conditions.
     * @param changedConditions The conditions that evaluate to true.
     * @return A list of all triggers of which the actions need to be executed.
     */
    def findTriggeredTriggers(changedConditions: List[TriggerCondition]): List[TriggerDeclaration] = {
        List()
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