package io.coral.actors.connector

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class KafkaConsumerActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  def this() = this(ActorSystem("KafkaConsumerActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  "A KafkaConsumerActor" should {



  }

}
