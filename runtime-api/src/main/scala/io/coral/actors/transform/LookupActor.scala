package io.coral.actors.transform

// akka
import akka.actor.{ActorLogging, Props}

//json goodness
import org.json4s._
import org.json4s.jackson.JsonMethods.render

// coral
import io.coral.actors.{SimpleEmitTrigger, CoralActor}
import io.coral.actors.transform.LookupActor._

object LookupActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      lookup   <- (json \ "attributes" \ "params" \ "lookup").extractOpt[Map[String, JObject]]
      key      <- (json \ "attributes" \ "params" \ "key").extractOpt[String]
      function <- determineFunction(json)
      matchType <- determineMatch(json)
    } yield {
      (key, lookup, function, matchType)
    }
  }

  private def determineMatch(json: JValue): Option[MatchType] = {
    val matchType = (json \ "attributes" \ "params" \ "match").extractOpt[String]
    matchType match {
      case Some("exact") => Some(Exact)
      case Some("startswith") => Some(StartsWith)
      case None => Some(Exact)
      case _ => None
    }
  }

  private def determineFunction(json: JValue): Option[LookupFunction] = {
    val function = (json \ "attributes" \ "params" \ "function").extractOpt[String]
    function match {
      case Some("enrich") => Some(Enrich)
      case Some("filter") => Some(Filter)
      case Some("check") => Some(Check)
      case _ => None
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[LookupActor], json))
  }

  abstract sealed class LookupFunction
  object Enrich extends LookupFunction
  object Filter extends LookupFunction
  object Check extends LookupFunction

  abstract sealed class MatchType
  object Exact extends MatchType
  object StartsWith extends MatchType
}

class LookupActor(json: JObject)
  extends CoralActor(json)
  with ActorLogging
  with SimpleEmitTrigger {

  val (key, lookup, function, matchType) = LookupActor.getParams(json).get

  override def simpleEmitTrigger(json: JObject): Option[JValue] = {
    for {
      value <- (json \ key).extractOpt[String]
    } yield {
      val lookupObject = determineLookup(value)

      function match {
        case Enrich => json merge render(lookupObject.getOrElse(JNothing))
        case Filter => lookupObject map (_ => json) getOrElse(JNull)
        case Check  => render(lookupObject.getOrElse(JNull))
      }
    }
  }

  private def determineLookup(value: String): Option[JObject] = {
    matchType match {
      case Exact => exactMatch(value)
      case StartsWith => startsWithMatch(value)
    }
  }

  private def exactMatch(value: String): Option[JObject] = {
    lookup.get(value)
  }

  private def startsWithMatch(value: String): Option[JObject] = {
    lookup.find{case (key, _) => value.startsWith(key)}.map(_._2)
  }
}