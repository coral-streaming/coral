package io.coral.database

import akka.actor.Props
import com.datastax.driver.core.{DataType, ResultSet, Session, Cluster}
import org.json4s.JValue
import org.json4s.JsonAST.JValue

import scala.concurrent.{Promise, Future}

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// coral
import io.coral.actors.CoralActor

import scalaz.{OptionT, Monad}
import scalaz.OptionT._

object CassandraActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      host <- (json \ "host").extractOpt[String]
      keyspace <- (json \ "keyspace").extractOpt[String]
    } yield {
      (host, keyspace)
    }
  }

  def apply(json: JValue): Option[Props] = {
    getParams(json).map(_ => Props(classOf[CassandraActor], json))
  }
}

class CassandraActor(json: JObject) extends CoralActor {
  def jsonDef = json

  val (host, keyspace) = CassandraActor.getParams(json).get

  override def preStart() {
    ensureConnection(host, keyspace)
  }

  var cluster: Cluster = _
  var session: Session = _
  var schema: JObject = _
  var tables = List.empty[String]

  def state = Map(
    ("connected", render(session != null && !session.isClosed)),
    ("keyspace",  render(keyspace)),
    ("schema",  render(schema)),
    ("tables",  render(tables))
  )

  def timer = JNothing

  // Stores the intermediate result for emit.
  // A select returns a resultset, any other
  // query type (update, insert, delete, ...) returns
  // a boolean indicating success or failure
  var result: Option[Either[ResultSet, Boolean]] = _

  def trigger = {
    json: JObject =>
      ensureConnection(host, keyspace)

      // Add this when you do not want a for comprehension:
      // OptionT.some(Future.successful({}))

      for {
        query <- getTriggerInputField[String](json \ "query")
        data <- OptionT.some(session.execute(query))
      } yield {
        // TODO: How to catch exception and return Right(false)
        if (query.startsWith("select")) {
          Some(Left(data))
        } else {
          Some(Right(true))
        }
      }
  }

  def emit = {
    json: JObject =>
      result match {
        case Some(Left(data)) =>
          renderResultSet(data)
        case Some(Right(successful)) =>
          render(("result" -> successful))
        case None =>
          JNothing
      }
  }

  def ensureConnection(host: String, keyspace: String) {
    // No need to connect if already having a valid session object
    if (session == null || session.isClosed) {
      log.info("CassandraActor not yet connected. Connecting now...")
      cluster = Cluster.builder().addContactPoint(host).build()
      session = cluster.connect(keyspace)
      schema = getSchema(session)
      tables = getTables(session)
    } else {
      log.info("CassandraActor already connected.")
    }
  }

  def getTables(session: Session): List[String] = {
    val result = scala.collection.mutable.ListBuffer.empty[String]

    val tablemetadata = session.getCluster.getMetadata
      .getKeyspace(keyspace).getTables.iterator()

    while (tablemetadata.hasNext) {
      result += tablemetadata.next().getName
    }

    result.toList
  }

  def getSchema(session: Session): JObject = {
    val result = session.getCluster.getMetadata.exportSchemaAsString()
    render(result).asInstanceOf[JObject]
  }
  
  // todo: ???
  def renderResultSet(data: ResultSet): JObject = {
    val it = data.iterator()
    val columns = data.getColumnDefinitions

    while (it.hasNext) {
      val row = it.next
    }

    val stringRep = DataType.serializeValue toString
    null
  }
}