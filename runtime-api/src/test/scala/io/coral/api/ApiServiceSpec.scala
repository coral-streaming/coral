package io.coral.api

import akka.actor.Props
import akka.pattern.ask
import io.coral.actors.Messages.{RegisterActorPath, RegisterActor, DeleteAllActors}
import io.coral.actors.{CoralActor, RuntimeActor}
import org.json4s.JObject
import org.scalatest.WordSpecLike
import org.specs2.mutable.BeforeAfter
import spray.http.HttpHeaders.RawHeader
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import JsonConversions._
import org.json4s.native.JsonMethods._

class ApiServiceSpec
  extends WordSpecLike
  with ScalatestRouteTest
  with ApiService
  with BeforeAfter {

  private val AcceptHeader = RawHeader("Accept", "application/vnd.api+json")

  private val coralActor = system.actorOf(Props(classOf[RuntimeActor], new DefaultModule(system.settings.config)), "coral")

  def actorRefFactory = system

  override def before() = {
    coralActor ? DeleteAllActors()
  }

  override def after() = {}

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

    "refuse a request without the proper Accept header" in {
      Get("/api/actors") ~> route ~> check {
        assert(status === StatusCodes.NotAcceptable)
      }
    }
  }
}

class TestActor1 extends CoralActor {
  def jsonDef = parse("""{}""")

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
