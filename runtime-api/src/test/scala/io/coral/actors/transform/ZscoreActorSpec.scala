package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.lib.SummaryStatistics
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ZscoreActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("coral"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(100.millis)
  implicit val formats = org.json4s.DefaultFormats

  def createZscoreActor(n: Int, by: String, field: String, score: Double): ZscoreActor = {
    val createJson = parse(
      s"""{ "type": "zscore",
         |"params": { "by": "${by}",
                                    |"field": "${field}",
                                                         |"score": ${score} } }""".stripMargin)
      .asInstanceOf[JObject]
    val props = CoralActorFactory.getProps(createJson).get
    val actorRef = TestActorRef[ZscoreActor](props, s"${n}")
    actorRef.underlyingActor
  }

  def createStatsActor(n: Int, field: String): StatsActor = {
    val createJson = parse( s"""{ "type": "stats", "params": { "field": "${field}" } }""")
      .asInstanceOf[JObject]
    val props = CoralActorFactory.getProps(createJson).get
    val actorRef = TestActorRef[StatsActor](props, s"${n}")
    actorRef.underlyingActor
  }

  "ZscoreActor" should {

    "obtain correct values from create json" in {
      val actor = createZscoreActor(1, "field1", "field2", 6.1)
      actor.by should be("field1")
      actor.field should be("field2")
      actor.score should be(6.1)
    }

    "have no state" in {
      val actor = createZscoreActor(2, "field1", "field2", 6.1)
      actor.state should be(Map.empty)
    }

    "have no timer action" in {
      val actor = createZscoreActor(3, "field1", "field2", 6.1)
      actor.timer should be(actor.notSet)
    }

    // this should be better separated, even if only from a unit testing point of view
    "process trigger and collect data" in {
      val zscore = createZscoreActor(4, by = "dummy", field = "val", score = 6.1)
      val stats = createStatsActor(5, field = "val")
      (1 to 5).foreach { _ => stats.stats.append(3.0) }
      (1 to 5).foreach { _ => stats.stats.append(1.0) }
      (1 to 5).foreach { _ => stats.stats.append(4.0) }
      (1 to 5).foreach { _ => stats.stats.append(2.0) }
      println(s"\nstats count=${stats.stats.count} avg=${stats.stats.average} sd=${stats.stats.populationSd}")
      println(s"\n${stats.self.path}")
      zscore.collectSources = Map("stats" -> "/user/5")
      implicit val fm = zscore.futureMonad
      val x = zscore.getCollectInputField[Long]("stats", "", "count")
      x.getOrElse(-1L).onComplete {
        case Success(y) => println(s"\n>>>>>>>> ${y}")
        case Failure(e) => println(s"\nFAILURE ${e}")
      }
    }
  }
}
