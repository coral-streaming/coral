package io.coral.actors

// scala
import scala.collection.immutable.{SortedMap, SortedSet}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

// akka
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

//spray utils
import spray.util.actorSystem

//json goodness
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._

// scalaz monad transformers
import scalaz.OptionT._
import scalaz.{Monad, OptionT}

//coral

import io.coral.actors.Messages._

sealed class TimerBehavior
object TimerExit     extends TimerBehavior
object TimerContinue extends TimerBehavior
object TimerNone     extends TimerBehavior

abstract class CoralActor extends Actor with ActorLogging {
  // begin: implicits and general actor init
  def actorRefFactory = context

  def jsonDef: JValue

  // transmit actor list
  // TODO: Why sorted, and why not simply a Set? Sort on what? Maybe make this hashset?
  var emitTargets = SortedSet.empty[ActorRef]

  // numeric id  or None or "external"
  // TODO: What is this actually used for?
  var collectSources = Map.empty[String, String] // zero or more alias to actorpath id

  // TODO: What is this actually used for?
  var inputJsonDef: JValue = JObject()

  // getting the default executor from the akka system
  implicit def executionContext: ExecutionContextExecutor = actorRefFactory.dispatcher

  implicit val timeout = Timeout(1000.milliseconds)

  // TODO: Where is it actually used?
  def askActor(a: String, msg: Any)  = actorRefFactory.actorSelection(a).ask(msg)
  def tellActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).!(msg)

  implicit val formats = org.json4s.DefaultFormats

  // TODO: Remove this completely and refactor
  implicit val futureMonad = new Monad[Future] {
    def point[A](a: => A): Future[A] = Future.successful(a)
    def bind[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa flatMap f
  }

  // Future[Option[A]] to Option[Future, A] using the OptionT monad transformer
  // TODO: What to do if you don't want a "by"? Option[by], or overloaded method
  def getCollectInputField[A](actorAlias: String, by: String, field: String)(implicit mf: Manifest[A]) = {
    // TODO: return Option[A] and case None => None
    val result = collectSources.get(actorAlias) match {
      case Some(actorPath) =>
        askActor(actorPath, GetFieldBy(field, by)).mapTo[JValue].map(json => json.extractOpt[A])
      case None => Future.failed(throw new Exception(s"Collect actor not defined"))
    }
    // TODO: return result
    optionT(result)
  }

  // TODO: remove these methods
  def getTriggerInputField[A](jsonValue: JValue)(implicit mf: Manifest[A]): OptionT[Future, A] = {
    val value = Future.successful(jsonValue.extractOpt[A])
    optionT(value)
  }

  // TODO: remove these methods
  def getTriggerInputField[A](jsonValue: JValue, defaultValue: A)(implicit mf: Manifest[A]): OptionT[Future, A] = {
    val value: Future[Option[A]] = Future.successful(Some(jsonValue.extractOrElse[A](defaultValue)))
    optionT(value)
  }

  // TODO: Remove the optionT(value) and return simply value
  def getActorResponse[A](path: String, msg: Any) = {
    val result = askActor(path, msg).mapTo[Option[A]]
    optionT(result)
  }

  // => means lazy evaluation
  def in[U](duration: FiniteDuration)(body: => U): Unit =
    actorSystem.scheduler.scheduleOnce(duration)(body)

  override def preStart() {
    timerInit()
  }

  // timer logic
  def timerInit() = {
    if (timerDuration > 0 && (timerMode == TimerExit || timerMode == TimerContinue))
      in(timerDuration.seconds) {
        self ! TimeoutEvent
      }
  }

  def timerDuration: Double = (jsonDef \ "attributes" \ "timeout" \ "duration").extractOrElse(0.0)

  def timerMode: TimerBehavior =
    (jsonDef \ "attributes" \ "timeout" \ "mode").extractOpt[String] match {
      case Some("exit") => TimerExit
      case Some("continue") => TimerContinue
      case _ => TimerNone
    }

  type Timer = JValue

  // TODO: Make noTimer the default
  def timer: Timer
  val noTimer: Timer = JNothing

  def receiveTimeout: Receive = {
    case TimeoutEvent =>
      transmit(timer)

      // depending on the configuration,
      // end the actor (gracefully) or ...
      // reset the timer

      timerMode match {
        case TimerContinue =>
          in(timerDuration.seconds) {
            self ! TimeoutEvent
          }
        case TimerExit =>
          tellActor("/user/coral", Delete(self.path.name.toLong))
        case _ => // do nothing
      }
  }

  // trigger
  // TODO: make an Future[JValue] out of this
  type Trigger =  JObject => OptionT[Future, Unit]
  // TODO: Redesign this also
  def trigger: Trigger
  val defaultTrigger : Trigger =
    json => OptionT.some(Future.successful({}))

  // emitting

  // TODO: Remove the emit method because there is already transmit()
  type Emit = JObject => JValue

  def emit: Emit
  val emitNothing: Emit = _    => JNothing
  val emitPass   : Emit = json => json

  // transmitting to the subscribing coral actors

  // actor.underlyingActor.emitTargets += probe
  // actor ! RegisterActor(probe)
  // TODO: rename this to registerActorAdmin or something
  def transmitAdmin: Receive = {
    case RegisterActor(r) =>
      emitTargets += r
  }

  def transmit(json:JValue) = {
    json match {
      case json: JObject =>
        emitTargets map (actorRef => actorRef ! json)
      case _ =>
    }
  }

  // TODO: Remove the old one if there is one otherwise you end up with two
  def propHandling: Receive = {
    case UpdateProperties(json) =>
      val triggerSource = (json \ "attributes" \ "input" \ "trigger")

      val triggerJsonDef = triggerSource match {
        case JString(v) => {
          tellActor(s"/user/coral/$v", RegisterActor(self))
          render ("trigger" -> triggerSource )
        }
        case _=> JObject()
      }

      val collectAliases = (json \ "attributes" \ "input" \ "collect")
      val result = collectAliases.extractOpt[Map[String, Any]] match {
        case Some(v) =>
          val x = v.keySet.map(k => (k, (collectAliases \ k)
            .extractOpt[String].map(v => s"/user/coral/$v")))
          (x.filter(_._2.isDefined).map(i => (i._1, i._2.get)).toMap,
            render("collect" -> collectAliases))
        case None =>
          (Map[String, String](), JObject())
      }

      // TODO: Rfactor this!
      collectSources = result._1
      val collectJsonDef = result._2
      inputJsonDef = triggerJsonDef merge collectJsonDef

      sender ! true
  }

  def resourceDesc: Receive = {
    // TODO: rename this by getState or something else
    case Get() =>
      sender ! (jsonDef
        merge render("attributes" -> render("state" -> render(state)))
        merge render("attributes" -> render("input" -> inputJsonDef)))
  }

  def execute(json:JObject, sender:Option[ActorRef]) = {
    // TODO: Refactor this, it will change when removing the OptionT
    val stage = trigger(json)
    val r = stage.run

    r.onSuccess {
      case Some(_) =>
        // TODO: emit will be removed
        val result = emit(json)
        transmit(result)
        // TODO: Why is the result sent back to the sender?
        sender.foreach(_ ! result)
      case None => log.warning("not processed")
    }

    r.onFailure {
      case _ => log.warning("actor execution")
    }
  }

  def jsonData: Receive = {
    case json: JObject =>
      // TODO: Remove the Shunt and remove the sender parameter
      execute(json,None)

    case Shunt(json) =>
      execute(json,Some(sender()))
  }

  // TODO: Why is the children variable here and not in the group by actor?
  var children = SortedMap.empty[String, Long]

  def receiveExtra:Receive = {case Unit => }

  def receive = jsonData           orElse
                stateReceive       orElse
                transmitAdmin      orElse
                propHandling       orElse
                resourceDesc       orElse
                receiveTimeout     orElse
                receiveExtra

  def state: Map[String, JValue]

  def stateResponse(x:String, by:Option[String], sender:ActorRef) = {
    // TODO: Refactor to case match
    if ( by.getOrElse("").isEmpty) {
      val value = state.get(x)
      // TODO: render not needed because it is already a JValue?
      sender ! render(value)
    } else {
      val found = children.get(by.get) flatMap (a => actorRefFactory.child(a.toString))

      found match {
        case Some(actorRef) =>
          actorRef forward GetField(x)
        case None =>
          sender ! render(JNothing)
      }
    }
  }

  def stateReceive: Receive = {
    case GetField(x) =>
      stateResponse(x, None, sender())
    case GetFieldBy(x, by) =>
      stateResponse(x, Some(by), sender())
  }
}