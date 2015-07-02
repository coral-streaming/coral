package io.coral.api

import akka.actor.Props
import akka.pattern.ask
import io.coral.actors.Messages.RegisterActorPath
import io.coral.actors.{CoralActor, RuntimeActor}
import org.json4s.JObject
import org.scalatest.WordSpecLike
import spray.http.HttpHeaders.{Location, RawHeader}
import spray.http.{HttpCharsets, ContentType, HttpEntity, StatusCodes}
import spray.httpx.marshalling.marshal
import spray.http.MediaTypes.`application/vnd.api+json`
import spray.testkit.ScalatestRouteTest
import JsonConversions._
import org.json4s.native.JsonMethods._

class ApiServiceSpec
  extends WordSpecLike
  with ScalatestRouteTest
  with ApiService {

  private val AcceptHeader = RawHeader("Accept", "application/vnd.api+json")

  private val ContentTypeHeader = RawHeader("Content-Type", "application/vnd.api+json")

  private val JsonApiContentType = ContentType(`application/vnd.api+json`, HttpCharsets.`UTF-8`)

  private val coralActor = system.actorOf(Props(classOf[RuntimeActor], new DefaultModule(system.settings.config)), "coral")

  def actorRefFactory = system

  val route = serviceRoute

  "The ApiService" should {
    "return an empty array when no actors are present" in {
      Get("/api/actors").withHeaders(AcceptHeader) ~> route ~> check {
        assert(status === StatusCodes.OK)
        assert(responseAs[JObject] === parse("""{ "data": [] }"""))
      }
    }

    "return the actors when actors are present" in {
      val actor1 = system.actorOf(Props(classOf[TestActor1]), "actor1")
      val actor2 = system.actorOf(Props(classOf[TestActor2]), "actor2")
      coralActor ? RegisterActorPath(1, actor1.path)
      coralActor ? RegisterActorPath(2, actor2.path)
      Get("/api/actors").withHeaders(AcceptHeader) ~> route ~> check {
        assert(status === StatusCodes.OK)
        assert(responseAs[JObject] === parse(
          """{ "data": [
            |{"id": "1", "type": "actors", "links": {"self": "http://example.com/api/actors/1"} },
            |{"id": "2", "type": "actors", "links": {"self": "http://example.com/api/actors/2"} }
            |] }""".stripMargin))
      }
    }

    "return the actor when it is present" in {
      val actor = system.actorOf(Props(classOf[TestActor1]), "actor")
      coralActor ? RegisterActorPath(100, actor.path)
      Get("/api/actors/100").withHeaders(AcceptHeader) ~> route ~> check {
        assert(status == StatusCodes.OK)
        assert(contentType == JsonApiContentType)
        assert(responseAs[JObject] === parse(
          """{ "data": {
            |"type": "actors",
            |"attributes": {
            |"type": "testactor",
            |"key": "value",
            |"state":{},
            |"input":{}
            |},
            |"id": "100",
            |}}""".stripMargin))
      }
    }

    "return a not found when the actor is not present" in {
      Get("/api/actors/101").withHeaders(AcceptHeader) ~> route ~> check {
        assert(status == StatusCodes.NotFound)
        assert(responseAs[JObject] === parse(
          """{ "errors":
            |[{"detail": "actorId 101 not found"}]
            |}""".stripMargin))
      }
    }

    "return the added actor" in {
      val jsonDef = parse(
        """{"data": {
          |"type": "actors",
          |"attributes": {"type": "httpbroadcast"}
          |}}""".stripMargin)
      val inputEntity = HttpEntity(`application/vnd.api+json`, marshal(jsonDef).right.get.data)
      Post("/api/actors").withEntity(inputEntity).withHeaders(AcceptHeader, ContentTypeHeader) ~> route ~> check {
        assert(status == StatusCodes.OK)
        assert(contentType == JsonApiContentType)
        assert(headers.contains(Location("http://example.com/api/actors/1")))
        assert(responseAs[JObject] == parse(
          """{ "data": {
            |"type": "actors",
            |"attributes": {
            |"type":"httpbroadcast"
            |"state":{},
            |"input":{}
            |},
            |"id": "1",
            |}}""".stripMargin))
      }
    }

    "refuse a request to modify an actor when the ids don't match" in {
      val actor = system.actorOf(Props(classOf[TestActor1]), "actor4")
      coralActor ? RegisterActorPath(300, actor.path)
      val jsonDef = parse(
        """{"data": {
          |"id": "999",
          |"type": "actors",
          |"attributes": {"type": "testactor", "input":{"trigger":{"in":{"type":"external"}}}}
          |}}""".stripMargin)
      val inputEntity = HttpEntity(`application/vnd.api+json`, marshal(jsonDef).right.get.data)
      Patch("/api/actors/300").withEntity(inputEntity).withHeaders(AcceptHeader, ContentTypeHeader) ~> route ~> check {
        assert(status == StatusCodes.Forbidden)
        assert(responseAs[JObject] == parse("""{"errors":[{"detail": "Id must be given and the same as in the URL"}]}"""))
      }
    }

    "refuse a request to modify an actor when no id given" in {
      val actor = system.actorOf(Props(classOf[TestActor1]), "actor5")
      coralActor ? RegisterActorPath(300, actor.path)
      val jsonDef = parse(
        """{"data": {
          |"type": "actors",
          |"attributes": {"type": "testactor", "input":{"trigger":{"in":{"type":"external"}}}}
          |}}""".stripMargin)
      val inputEntity = HttpEntity(`application/vnd.api+json`, marshal(jsonDef).right.get.data)
      Patch("/api/actors/300").withEntity(inputEntity).withHeaders(AcceptHeader, ContentTypeHeader) ~> route ~> check {
        assert(status == StatusCodes.Forbidden)
        assert(responseAs[JObject] == parse("""{"errors":[{"detail": "Id must be given and the same as in the URL"}]}"""))
      }
    }

    "refuse a request to add an actor without a data field" in {
      val jsonDef = parse("""{"type": "actors"}""")
      val entity = HttpEntity(`application/vnd.api+json`, marshal(jsonDef).right.get.data)
      Post("/api/actors").withEntity(entity).withHeaders(AcceptHeader, ContentTypeHeader) ~> route ~> check {
        assert(status == StatusCodes.BadRequest)
        assert(responseAs[JObject] == parse(
          """{"errors": [{"detail": "no data key present"}]}"""
        ))
      }
    }

    "refuse a request to add an actor with a client generated id" in {
      val jsonDef = parse("""{"data": {"type": "actors", "id": "1"}}""")
      val entity = HttpEntity(`application/vnd.api+json`, marshal(jsonDef).right.get.data)
      Post("/api/actors").withEntity(entity).withHeaders(AcceptHeader, ContentTypeHeader) ~> route ~> check {
        assert(status == StatusCodes.Forbidden)
        assert(responseAs[JObject] == parse(
          """{"errors": [{"detail": "Client generated id not allowed"}]}"""
        ))
      }
    }

    "refuse a request without the proper Accept header" in {
      Get("/api/actors") ~> route ~> check {
        assert(status === StatusCodes.NotAcceptable)
      }
    }

    "refuse a request with a include parameter" in {
      Get("/api/actors?include=something").withHeaders(AcceptHeader) ~> route ~> check {
        assert(status === StatusCodes.BadRequest)
        assert(responseAs[JObject] === parse("""{ "errors": [{"detail": "include not supported"}] }"""))
      }
    }

    "refuse a requst with a sort parameter" in {
      Get("/api/actors?sort=something").withHeaders(AcceptHeader) ~> route ~> check {
        assert(status === StatusCodes.BadRequest)
        assert(responseAs[JObject] === parse("""{ "errors": [{"detail": "sort not supported"}] }"""))
      }
    }

    "refuse content without the proper Content-Type header" in {
      Post("/api/actors").withHeaders(AcceptHeader) ~> route ~> check {
        assert(status == StatusCodes.UnsupportedMediaType)
      }
    }
  }
}

class TestActor1 extends CoralActor {
  def jsonDef = parse("""{"type": "actors", "attributes": {"type": "testactor", "key": "value"}}""")

  def timer = noTimer

  def state = Map.empty

  def emit = emitNothing

  def trigger = defaultTrigger
}

class TestActor2 extends CoralActor {
  def jsonDef = parse("""{}""")

  def timer = noTimer

  def state = Map.empty

  def emit = emitNothing

  def trigger = defaultTrigger
}
