package io.coral.actors.connector

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods._
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

  def apiJson(s: String): JValue = parse( s"""{"type": "actors", "attributes": $s }""")

  "A KafkaConsumerActor" should {

    "read kafka parameters" in {
      val json = apiJson(
        """{ "type": "kafkaConsumer",
          |  "params": {
          |    "kafka" : {
          |      "group.id": "xyz",
          |      "zookeeper.connect": "localhost:2181"
          |    },
          |    "topic": "abc"
          | } }""".stripMargin)
      lazy val (properties, topic) = KafkaConsumerActor.getParams(json).get
      topic shouldBe "abc"
      properties.size shouldBe 4
      properties.getProperty("group.id") shouldBe "xyz"
      properties.getProperty("zookeeper.connect") shouldBe "localhost:2181"
      properties.getProperty("consumer.timeout.ms") shouldBe "500"
      properties.getProperty("auto.commit.enable") shouldBe "false"
    }




  }

}
