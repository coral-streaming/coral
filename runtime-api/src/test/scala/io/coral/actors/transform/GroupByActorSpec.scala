package io.coral.actors.transform

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import io.coral.actors.RuntimeActor
import io.coral.api.DefaultModule
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.languageFeature.postfixOps

class GroupByActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  def this() = this(ActorSystem("GroupByActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  val runtime = system.actorOf(Props(classOf[RuntimeActor], new DefaultModule()), "coral")

  implicit val timeout = Timeout(100 millis)
  implicit val formats = org.json4s.DefaultFormats
  implicit val injector = new DefaultModule()

  // here is a dependency on the stats actor
  // in the current situation (the CoralActorFactory) it seems unavoidable to depend in some tests on an existing actor instead of injecting a test actor
  def statsGroupBy: GroupByActor = {
    val createJson = parse(
      """{ "type": "stats",
        |  "params": { "field": "amount" },
        |  "group": { "by": "tag" }
        | }""".stripMargin
    ).asInstanceOf[JObject]
    TestActorRef[GroupByActor](GroupByActor(createJson).get).underlyingActor
  }

  "A GroupByActor" should {

    "Extract the the create json" in {
      val createJson = parse(
        """{ "type": "bla",
          |  "bla": "bla bla",
          |  "group": { "by": "some tag" },
          |  "more": "bla bla bla"
          | }""".stripMargin
      ).asInstanceOf[JObject]
      val actor = TestActorRef[GroupByActor](GroupByActor(createJson).get).underlyingActor
      val expectedChildJson = parse(
        """{ "type": "bla",
          |  "bla": "bla bla",
          |  "more": "bla bla bla"
          | }""".stripMargin
      )
      actor.jsonDef should be(createJson)
      actor.jsonChildrenDef should be(expectedChildJson)
      actor.by should be("some tag")
    }

    "Initialize without children" in {
      val actor = statsGroupBy
      actor.actors should be(Map.empty[String, Long])
      actor.state should be(Map(("actors", render(Map.empty[String, Long]))))
    }

    "Emit nothing by itself" in {
      val actor = statsGroupBy
      actor.emit(parse("{}").asInstanceOf[JObject]) should be(JNothing)
    }

    "Have no timer action of itself" in {
      val actor = statsGroupBy
      actor.timer should be(JNothing)
    }

    "Create a new child when triggered for non existing tag" in {
      val actor = statsGroupBy
      actor.actors.size should be(0)
      val result = actor.trigger(parse( """{ "tag": "a", "amount": 1.1 }""").asInstanceOf[JObject])
      whenReady(result.run) { _ => actor.actors.size should be(1) }
    }

    "Create no new child when triggered with existing group by field value" in {
      val actor = statsGroupBy
      actor.actors.size should be(0)
      val result1 = actor.trigger(parse( """{ "tag": "a", "amount": 1.1 }""").asInstanceOf[JObject])
      whenReady(result1.run) { _ => actor.actors.size should be(1) }
      val result2 = actor.trigger(parse( """{ "tag": "a", "amount": 2.2 }""").asInstanceOf[JObject])
      whenReady(result2.run) { _ => actor.actors.size should be(1) }
    }

    "Create a new child when triggered with a new group by field value" in {
      val actor = statsGroupBy
      actor.actors.size should be(0)
      val result1 = actor.trigger(parse( """{ "tag": "a", "amount": 1.1 }""").asInstanceOf[JObject])
      whenReady(result1.run) { _ => actor.actors.size should be(1) }
      val result2 = actor.trigger(parse( """{ "tag": "b", "amount": 1.1 }""").asInstanceOf[JObject])
      whenReady(result2.run) { _ => actor.actors.size should be(2) }
    }
  }

}
