package io.coral.actors

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import akka.util.Timeout
import io.coral.actors.Messages.{GetField, RegisterActor, UpdateProperties}
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.collection.immutable.SortedSet
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success
import scalaz.OptionT

class CoralActorSpec(_system: ActorSystem)
  extends TestKit(_system)
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  def this() = this(ActorSystem("coral"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  implicit val timeout = Timeout(100 millis)
  implicit val formats = org.json4s.DefaultFormats


  class TestCoralActor(json: String) extends CoralActor {
    override def jsonDef: JValue = parse(json)

    override def timer: JValue = parse( s"""{ "test": "timer" }""")

    override def state: Map[String, JValue] = Map("testkey" -> render("testvalue"))

    override def emit: (JObject) => JValue = _ => parse( """{ "test": "emit" }""")

    override def trigger: (JObject) => OptionT[Future, Unit] = noProcess

    override def postStop(): Unit = transmit(parse( """{ "test": "stopped" }"""))
  }

  val coralMain = TestActorRef[TestCoralActor](Props(new TestCoralActor("{}")), "coral")

  def createTestCoralActor(json: String = "{}", name: String = "") = {
    val props = Props(new TestCoralActor(json))
    val ref =
      if (name == "") TestActorRef[TestCoralActor](props)
      else TestActorRef[TestCoralActor](props, coralMain, name)
    ref.underlyingActor
  }

  "A CoralActor" should {

    "Require an implementation of the jsonDef function" in {
      val coral = createTestCoralActor( """{ "test": "jsonDef" }""")
      coral.jsonDef should be(parse( """{ "test": "jsonDef" }"""))
    }

    "Have a askActor method to ask by name" in {
      val coral = createTestCoralActor()
      val probe = TestProbe()
      val result = coral.askActor(probe.ref.path.toString, "ask")
      probe.expectMsg("ask")
      probe.reply("ask:response")
      assert(result.isCompleted && result.value == Some(Success("ask:response")))
    }

    "Have a tellActor method to tell by name" in {
      val coral = createTestCoralActor()
      val probe = TestProbe()
      coral.tellActor(probe.ref.path.toString, "tell")
      probe.expectMsg("tell")
    }

    "Get an actor response as OptionT" in {
      val coral = createTestCoralActor()
      val probe = TestProbe()
      val path = probe.ref.path.toString
      val result = coral.getActorResponse[Long](path, "msg1")
      probe.expectMsg("msg1")
      probe.reply(Some(42L))
      whenReady(result.run) {
        value => value should be(Some(42L))
      }
    }

    "Have an 'in' method" in {
      val coral = createTestCoralActor()
      val probe = TestProbe()
      coral.in(10 millis) {
        coral.tellActor(probe.ref.path.toString, "msg2")
      }
      probe.expectMsg(100 millis, "msg2")
    }

  }

  "A CoralActor trigger" should {
    "Be defined in concrete implementations" in {
      val coral = createTestCoralActor()
      val result = coral.trigger(parse( """{ "a":"b" }""").asInstanceOf[JObject]) //should be(OptionT.some(Future.successful({})))
      whenReady(result.run) {
        value => value should be(Some(()))
      }
    }

    "Get the trigger field as OptionT" in {
      val coral = createTestCoralActor()
      val result = coral.getTriggerInputField[Double](parse("2.71"))
      whenReady(result.run) {
        value => value should be(Some(2.71))
      }
    }

    "Ignore an UpdateProperties message without information" in {
      val coral = createTestCoralActor()
      coral.self ! UpdateProperties(parse( """{}""").asInstanceOf[JObject])
      expectMsg(true)
    }

    "Handle an UpdateProperties message with trigger connection" in {
      val coral = createTestCoralActor()
      val other = createTestCoralActor(name = "test")
      println(s"#${other.self.path.toString}")
      coral.self ! UpdateProperties(parse( """{"input": {"trigger":{"in": {"type": "external"}}}}""").asInstanceOf[JObject])
      expectMsg(true)
      coral.self ! UpdateProperties(parse( """{"input": {"trigger":{"in": {"type": "actor", "source": "test"}}}}""").asInstanceOf[JObject])
      other.emitTargets should be(SortedSet(coral.self))
      expectMsg(true)
    }

  }

  "CoralActor emit" should {

    "Be defined in concrete implementations" in {
      val coral = createTestCoralActor()
      coral.emit(parse( """{  }""").asInstanceOf[JObject]) should be(parse( """{ "test": "emit" }"""))
    }

    "Have process a RegisterActor message" in {
      val coral = createTestCoralActor()
      val probe = TestProbe()
      coral.self ! RegisterActor(probe.ref)
      coral.emitTargets should be(SortedSet(probe.ref))
    }

    "Have a transmit method" in {
      val coral = createTestCoralActor()
      val probe1 = TestProbe()
      val probe2 = TestProbe()
      coral.emitTargets += probe2.ref
      coral.emitTargets += probe1.ref
      val json = parse( """{ "test": "transmit" }""")
      coral.transmit(json)
      probe1.expectMsg(json)
      probe2.expectMsg(json)
    }

  }

  "A CoralActor state" should {

    "Be defined in concrete implementations" in {
      val coral = createTestCoralActor()
      coral.state should be(Map("testkey" -> render("testvalue")))
    }

    "Be accessible with a GetField() message" in {
      val coral = createTestCoralActor()
      val json = parse( """{}""")
      coral.self ! GetField("testkey")
      expectMsg(JString("testvalue"))
      coral.self ! GetField("non-existing key")
      expectMsg(JNothing)
    }

  }

  "CoralActor collect" should {

    "Obtain state of other actors" in {
      val coral = createTestCoralActor()
      val probe = TestProbe()
      val path = probe.ref.path.toString
      coral.collectSources = Map("test" -> path)
      val result = coral.getCollectInputField[Int]("test", "", "testField")
      probe.expectMsg(GetField("testField"))
      probe.reply(JInt(42))
      whenReady(result.run) {
        value => value should be(Some(42))
      }
    }

    "Obtain state of other actors with subpath" in {
      val coral = createTestCoralActor()
      val probe = TestProbe()
      val tmp = probe.ref.path.toString.split("/").reverse
      val subpath = tmp.head
      val path = tmp.tail.reverse.mkString("/")
      coral.collectSources = Map("test" -> path)
      val result = coral.getCollectInputField[Int]("test", subpath, "testField")
      probe.expectMsg(GetField("testField"))
      probe.reply(JInt(42))
      whenReady(result.run) {
        value => value should be(Some(42))
      }
    }

    "Fail to obtain state of actors not in collectSources" in {
      val coral = createTestCoralActor()
      val thrown = intercept[Exception] {
        val result = coral.getCollectInputField[Int]("test", "", "testField")
      }
      thrown.getMessage should be("Collect actor not defined")
    }

  }

  "A CoralActor timer" should {

    "Be defined in concrete implementations" in {
      val coral = createTestCoralActor()
      coral.timer should be(parse( """{ "test": "timer" }"""))
    }

    "Accept a timer parameter" in {
      val coral = createTestCoralActor( s"""{ "timeout": { "duration": 13, "mode": "exit" } }""")
      coral.timerDuration should be(13L)
      coral.timerMode should be(TimerExit)
      val probe = TestProbe()
      coral.emitTargets += probe.ref
      probe.expectMsg(parse( """{ "test": "timer" }"""))
    }

    "Stop with timer mode 'exit'" in {
      val coral = createTestCoralActor( s"""{ "timeout": { "duration": 13, "mode": "exit" } }""")
      val probe = TestProbe()
      coral.emitTargets += probe.ref
      probe.expectMsg(parse( """{ "test": "timer" }"""))
      probe.expectMsg(parse( """{ "test": "stopped" }"""))
    }

    "Emit the timer json when timer mode = 'continue'" in {
      val coral = createTestCoralActor( s"""{ "timeout": { "duration": 5, "mode": "continue" } }""")
      val probe = TestProbe()
      coral.emitTargets = SortedSet(probe.ref)
      println(s"\n${coral.timerMode}#")
      probe.expectMsg(parse( """{ "test": "timer" }"""))
    }

    "Ignore an unknown timer mode" in {
      val coral = createTestCoralActor( s"""{ "timeout": { "duration": 5, "mode": "doesnotexist" } }""")
      coral.timerMode should be(TimerNone)
    }


  }
}
