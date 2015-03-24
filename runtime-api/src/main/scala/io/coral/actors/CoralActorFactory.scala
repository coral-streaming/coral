package io.coral.actors

//json

import io.coral.actors.database.CassandraActor
import org.json4s._

//coral
import io.coral.actors.transform._

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
                case "window"     => WindowActor(json)
			}
		} yield props

		groupByProps orElse actorProps
	}
}