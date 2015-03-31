package io.coral.actors.transform

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import io.coral.actors.CoralTestActor
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.languageFeature.postfixOps

class GroupByActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  def this() = this(ActorSystem("StatsActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }



  implicit val timeout = Timeout(100 millis)

  "A GroupByActor" should {
    //val coral = CoralTestActor()
    println(s"\n ### ${this.self.path.toString}")

  }

}
