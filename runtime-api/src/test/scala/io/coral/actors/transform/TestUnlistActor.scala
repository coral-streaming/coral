import akka.actor.{ActorSystem, Props}
import akka.testkit._
import akka.util.Timeout
import io.coral.actors.CoralActorFactory
import io.coral.actors.Messages.{Shunt, Emit, Trigger}
import io.coral.actors.transform.{UnlistActor, HttpClientActor}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.duration._

class TestUnlistActor(_system: ActorSystem) extends TestKit(_system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

    def this() = this(ActorSystem("unlistActor"))

    override def afterAll() {
        TestKit.shutdownActorSystem(system)
    }

    implicit val timeout = Timeout(1.seconds)

    "an UnlistActor" should {
        "Unlist items from a given list" in {
            val probe = TestProbe()
            val instantiationJson = parse( """{ "type": "unlist", "params": { "field": "data" } }""").asInstanceOf[JObject]
            val props: Props = CoralActorFactory.getProps(instantiationJson).get
            val actorRef = TestActorRef[UnlistActor](props)
            actorRef.underlyingActor.emitTargets += probe.ref

            val triggerJson = parse(
                """{ "data": [
                  | { "name": "object1" },
                  | { "name": "object2" },
                  | { "name": "object3" },
                  | { "name": "object4" }
                  |] }""".stripMargin).asInstanceOf[JObject]

            actorRef ! Trigger(triggerJson)

            probe.expectMsg(parse("""{ "name": "object1" } """))
            probe.expectMsg(parse("""{ "name": "object2" } """))
            probe.expectMsg(parse("""{ "name": "object3" } """))
            probe.expectMsg(parse("""{ "name": "object4" } """))
        }

        "Send nothing on an empty list" in {
            val probe = TestProbe()
            val instantiationJson = parse( """{ "type": "unlist", "params": { "field": "data" } }""").asInstanceOf[JObject]
            val props: Props = CoralActorFactory.getProps(instantiationJson).get
            val actorRef = TestActorRef[UnlistActor](props)
            actorRef.underlyingActor.emitTargets += probe.ref

            val triggerJson = parse(
                """{ "data": [] }""".stripMargin).asInstanceOf[JObject]

            actorRef ! Trigger(triggerJson)

            probe.expectNoMsg()
        }

        "Send nothing on missing data field" in {
            val probe = TestProbe()
            val instantiationJson = parse( """{ "type": "unlist", "params": { "field": "data" } }""").asInstanceOf[JObject]
            val props: Props = CoralActorFactory.getProps(instantiationJson).get
            val actorRef = TestActorRef[UnlistActor](props)
            actorRef.underlyingActor.emitTargets += probe.ref

            val triggerJson = parse(
                """{ "notdata": [
                  | { "name": "object1" },
                  | { "name": "object2" },
                  | { "name": "object3" },
                  | { "name": "object4" }
                  |] }""".stripMargin).asInstanceOf[JObject]

            actorRef ! Trigger(triggerJson)

            probe.expectNoMsg()
        }
    }
}