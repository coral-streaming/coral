package io.coral.actors

import org.json4s._
import io.coral.actors.transform._
import io.coral.database.CassandraActor

object CoralActorFactory {
  def getProps(json: JValue) = {
    implicit val formats = org.json4s.DefaultFormats

    // check for grouping, if so generate a group actor and move on ...
    // otherwise, generate the proper actor
    val groupByProps = (json \ "group" \ "by").extractOpt[String] match {
      case Some(x) => GroupByActor(json)
      case None => None
    }

    val actorProps = for {
      actorType <- (json \ "type").extractOpt[String]

      props <- actorType match {
        case "fsm"        => FsmActor(json)
        case "zscore"     => ZscoreActor(json)
        case "stats"      => StatsActor(json)
        case "lookup"     => LookupActor(json)
        case "httpserver" => HttpServerActor(json)
        case "httpclient" => HttpClientActor(json)
        case "cassandra"  => CassandraActor(json)
        case "threshold"  => ThresholdActor(json)
      }
    } yield props

    groupByProps orElse actorProps
  }
}