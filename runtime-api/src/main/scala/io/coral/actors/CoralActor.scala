package io.coral.actors

// scala
import scala.collection.immutable.SortedSet
import scala.concurrent.Future
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

object TimerExit extends TimerBehavior

object TimerContinue extends TimerBehavior

object TimerNone extends TimerBehavior

trait CoralActor extends Actor with ActorLogging {
  // begin: implicits and general actor init
  def actorRefFactory = context

  def jsonDef: JValue

  // transmit actor list
  var emitTargets = SortedSet.empty[ActorRef]
  var triggerSource: Option[String] = None

  // numeric id  or None or "external"
  var collectSources = Map.empty[String, String] // zero or more alias to actorpath id

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val timeout = Timeout(1000.milliseconds)

  def askActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).ask(msg)

  def tellActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).!(msg)

  implicit val formats = org.json4s.DefaultFormats

  implicit val futureMonad = new Monad[Future] {
    def point[A](a: => A): Future[A] = Future.successful(a)

    def bind[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa flatMap f
  }

  // Future[Option[A]] to Option[Future, A] using the OptionT monad transformer
  def getCollectInputField[A](actorAlias: String, subpath: String, field: String)(implicit mf: Manifest[A]) = {
    val result = collectSources.get(actorAlias) match {
      case Some(actorPath) =>
        val path = if (subpath == "") actorPath else s"$actorPath/$subpath"
        askActor(path, GetField(field)).mapTo[JValue].map(json => json.extractOpt[A])
      case None => Future.failed(throw new Exception(s"Collect actor not defined"))
    }
    optionT(result)
  }

  def getTriggerInputField[A](jsonValue: JValue)(implicit mf: Manifest[A]) = {
    val value = Future.successful(jsonValue.extractOpt[A])
    optionT(value)
  }

  def getActorResponse[A](path: String, msg: Any) = {
    val result = askActor(path, msg).mapTo[Option[A]]
    optionT(result)
  }

  def in[U](duration: FiniteDuration)(body: => U): Unit =
    actorSystem.scheduler.scheduleOnce(duration)(body)

  override def preStart() {
    if (timerDuration > 0 && (timerMode == TimerExit || timerMode == TimerContinue))
      in(timerDuration.millis) {
        self ! TimeoutEvent
      }
  }

  def timerDuration: Long = (jsonDef \ "timeout" \ "duration").extractOrElse(0L)

  def timerMode: TimerBehavior =
    (jsonDef \ "timeout" \ "mode").extractOpt[String] match {
      case Some("exit") => TimerExit
      case Some("continue") => TimerContinue
      case _ => TimerNone
    }

  def timer: JValue

  def receiveTimeout: Receive = {
    case TimeoutEvent =>
      transmit(timer)

      // depending on the configuration,
      // end the actor (gracefully) or ...
      // reset the timer

      timerMode match {
        case TimerContinue =>
          in(timerDuration.millis) {
            self ! TimeoutEvent
          }
        case TimerExit =>
          self ! PoisonPill
        case _ => // do nothing
      }
  }

  def trigger: JObject => OptionT[Future, Unit]

  def noProcess(json: JObject): OptionT[Future, Unit] = {
    OptionT.some(Future.successful({}))
  }

  def emit: JObject => JValue

  val notSet = JNothing
  val doNotEmit: JObject => JValue = _ => JNothing
  val passThroughEmit: JObject => JValue = json => json

  def transmitAdmin: Receive = {
    case RegisterActor(r) =>
      log.warning(s"registering ${r.path.toString}")
      emitTargets += r
  }

  def transmit: JValue => Unit = {
    json => json match {
      case v: JObject =>
        emitTargets map (actorRef => actorRef ! v)
      case _ =>
    }
  }

  def propHandling: Receive = {
    case UpdateProperties(json) =>
      // update trigger
      triggerSource = (json \ "input" \ "trigger" \ "in" \ "type").extractOpt[String]
      triggerSource.getOrElse("none") match {
        case "none" =>
        case "external" =>
        case "actor" =>
          val source = (json \ "input" \ "trigger" \ "in" \ "source").extractOpt[String]
          source map { v =>
            tellActor(s"/user/coral/$v", RegisterActor(self))
          }

        case _ =>
      }

      val collectAliases = (json \ "input" \ "collect").extractOpt[Map[String, Any]]
      collectSources = collectAliases match {
        case Some(v) =>
          val x = v.keySet.map(k => (k, (json \ "input" \ "collect" \ k \ "source")
            .extractOpt[Int].map(v => s"/user/coral/$v")))
          x.filter(_._2.isDefined).map(i => (i._1, i._2.get)).toMap
        case None =>
          Map()
      }

      sender ! true
  }

  def resourceDesc: Receive = {
    case Get() =>
      sender ! render(("actors", render(Map(("def", jsonDef), ("state", render(state))))))
  }

  def execute(json:JObject, sender:Option[ActorRef]) = {
    val stage = trigger(json)
    val r = stage.run

    r.onSuccess {
      case Some(_) =>
        val result = emit(json)
        transmit(result)
        sender.foreach(_ ! result)

      case None => log.warning("not processed")
    }

    r.onFailure {
      case _ => log.warning("actor execution")
    }
  }

  def jsonData: Receive = {
    case json: JObject =>
      execute(json,None)

    case Shunt(json) =>
      execute(json,Some(sender))
  }

  def receive = jsonData        orElse
                stateReceive    orElse
                transmitAdmin   orElse
                propHandling    orElse
                resourceDesc    orElse
                receiveTimeout

  def state: Map[String, JValue]

  def stateReceive: Receive = {
    case GetField(x) =>
      val value = state.get(x)

      if (value == None) {
        sender ! render(JNothing)
      } else {
        sender ! render(value)
      }
  }
}