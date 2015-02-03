package com.natalinobusa.examples

// scala

import scala.collection.immutable.{HashMap, SortedSet, SortedMap}
import scala.concurrent.Future
import scala.concurrent.duration._

// akka
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._

//json goodness
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

// scalaz monad transformers
import scalaz.{OptionT, Monad}
import scalaz.OptionT._

// First attempt to describe a user DSL for collecting values
import scala.reflect.{ClassTag, Manifest}

// Inter-actor messaging
import com.natalinobusa.examples.models.Messages._

// Static code generation
import com.natalinobusa.macros.expose

object CoralActorFactory {
  def getProps(json:JValue) = {
    // for now just post a zscore Actor
    // todo: how about a factory? how about json to class?
    // a bit ugly, but it will do for now
    implicit val formats = org.json4s.DefaultFormats

    // check for grouping, if so generate a group actor and move on ...
    // otherwise, generate the proper actor
    println(pretty(json))
    val groupByProps = (json \ "group" \ "by").extractOpt[String] match {
      case Some(x) => GroupByActor(json)
      case None => None
    }

    println(groupByProps.toString)

    val actorProps = for {
      actorType <- (json \ "type").extractOpt[String]

      props <- actorType match {
        case "zscore" => ZscoreActor(json)
        case "histogram" => HistogramActor(json)
        case "rest" => RestActor(json)
      }
    } yield props

    groupByProps orElse actorProps
  }
}

class RuntimeActor extends Actor with ActorLogging {

  def actorRefFactory = context

  var actors = SortedMap.empty[Long, ActorPath]
  var count = 0L

  def receive = {
    case CreateActor(json) =>
      val props  = CoralActorFactory.getProps(json)

      val actorId = props map { p =>
        log.warning(p.toString)
        count += 1
        val id = count
        val actor = actorRefFactory.actorOf(p, s"$id")
        actors += (id -> actor.path)
        id
      }

      sender ! actorId

    case RegisterActorPath(id, path) =>
      actors += (id -> path)

    case GetCount =>
      count += 1
      sender ! Some(count)

    case ListActors =>
          sender ! actors.keys.toList
    //
    //    case Delete(id) =>
    //      directory.get(id).map(e => actorRefFactory.actorSelection(e._1) ! PoisonPill)
    //      directory -= id
    //      sender ! true
    //
    //    case  Get(id) =>
    //      val resource = directory.get(id).map( e => e._2 )
    //      log.info(s"streams get stream id $id, resource ${resource.toString} ")
    //      sender ! resource
    //
    case  GetActorPath(id) =>
      val path = actors.get(id)
      log.info(s"streams get stream id $id, path ${path.toString} ")
      sender ! path
  }
}

// metrics actor example
trait CoralActor extends Actor with ActorLogging {

  // begin: implicits and general actor init
  def actorRefFactory = context

  def jsonDef:JValue

  // transmit actor list
  var emitTargets     = SortedSet.empty[ActorRef]
  
  var triggerSource:Option[String]  = None        // numeric id  or None or "external"
  var collectSources = Map.empty[String, String]  // zero or more alias to actorpath id

  implicit def executionContext = actorRefFactory.dispatcher

  implicit val timeout = Timeout(10.milliseconds)

  def  askActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).ask(msg)
  def tellActor(a: String, msg: Any) = actorRefFactory.actorSelection(a).!(msg)

  implicit val formats = org.json4s.DefaultFormats

  implicit val futureMonad = new Monad[Future] {
    def point[A](a: => A): Future[A] = Future.successful(a)
    def bind[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa flatMap f
  }

  // update properties (for now just fix trigger and collect lists)

  // Future[Option[A]] to Option[Future, A] using the OptionT monad transformer
  def getCollectInputField[A](actorAlias: String, subpath:String, field: String)(implicit mf: Manifest[A]) = {
    val result = collectSources.get(actorAlias) match {
      case Some(actorPath) =>
        val path = if (subpath == "") actorPath else s"$actorPath/$subpath"
        askActor(path, GetField(field)).mapTo[JValue].map(json => (json \ field).extractOpt[A])
      case None    => Future.failed(throw new Exception(s"Collect actor not defined"))
    }
    optionT(result)
  }

  def getTriggerInputField[A](jsonValue: JValue)(implicit mf: Manifest[A]) = {
    val value = Future.successful(jsonValue.extractOpt[A])
    optionT(value)
  }

  def getActorResponse[A](path: String, msg:Any) = {
    val result = askActor(path, msg).mapTo[Option[A]]
    optionT(result)
  }

  // todo: ideas
  // can you fold this with become/unbecome and translate into a tell pattern rather the an ask pattern?

  def trigger: JObject => OptionT[Future, Unit]

  def noProcess(json: JObject): OptionT[Future, Unit] = {
    OptionT.some(Future.successful({}))
  }

  def emit: JObject => JValue
  val doNotEmit: JObject => JValue       = _ => JNothing
  val passThroughEmit: JObject => JValue = json => json

  def transmitAdmin: Receive = {
    case RegisterActor(r) =>
      log.warning(s"registering ${r.path.toString}")
      emitTargets += r
  }

  // narrow cast the result of the emit function to the recipient list
  def transmit: JValue => Unit = {
     json => json match {
       case v:JObject =>
         emitTargets map (actorRef => actorRef ! v)
       case _ =>
     }
  }

  // this become link
  def propertiesHandling: Receive = {
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

      // update collectlist
      // ugliest code ever :( not my best day
      val collectAliases = (json \ "input" \ "collect").extractOpt[Map[String, Any]]
      collectSources = collectAliases match {
        case Some(v) => {
          val x = v.keySet.map(k => (k, (json \ "input" \ "collect" \ k \ "source").extractOpt[Int].map(v => s"/user/coral/$v")))
          x.filter(_._2.isDefined).map(i => (i._1, i._2.get)).toMap
        }
        case None => Map()
      }

      sender ! true
  }

  def resourceDesc: Receive = {
    case Get =>
      sender ! render( ("actors", render(Map(("def", jsonDef), ("state", render(state))))) )
  }

  def jsonData: Receive = {
    // triggers (sensitivity list)
    case json: JObject =>
      val stage = trigger(json)

      // set the stage in motion ...
      val r = stage.run

      r.onSuccess {
        case Some(_) => transmit(emit(json))
        case None => log.warning("some variables are not available")
      }

      r.onFailure {
        case _ => log.warning("oh no, timeout or other serious exceptions!")
      }

  }

  def receive = jsonData orElse stateReceive orElse transmitAdmin orElse propertiesHandling orElse resourceDesc

  // state is a map

  def state:Map[String, JValue]

  def stateReceive:Receive = {
    case GetField(x) => {
      val value = state.get(x)
      sender ! render(state)
    }
  }

}

object HistogramActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json:JValue) = {
    for {
    // from trigger data
      field <- (json \ "params" \ "field").extractOpt[String]
    } yield {
      field
    }
  }

  def apply(json:JValue):Option[Props] = {
    getParams(json).map(_ =>  Props(classOf[HistogramActor], json))
    // todo: take better care of exceptions and error handling
  }

}


//an actor with state
class HistogramActor(json: JObject) extends CoralActor {

  def jsonDef = json
  val field   = HistogramActor.getParams(json).get

  // user defined state
  // todo: the given state should be persisted

  var count    = 0L
  var avg      = 0.0
  var sd       = 0.0
  var `var`    = 0.0

  def state = Map(
    ("count", render(count)),
    ("avg",   render(avg)),
    ("sd",    render(Math.sqrt(`var`)) ),
    ("var",   render(`var`))
  )

  // private variables not exposed
  var avg_1    = 0.0

  def trigger = {
    json: JObject =>
      for {
      // from trigger data
        value <- getTriggerInputField[Double](json \ field)
      } yield {
        // compute (local variables & update state)
        count match {
          case 0 =>
            count = 1
            avg_1 = value
            avg = value
          case _ =>
            count += 1
            avg_1 = avg
            avg = avg_1 * (count - 1) / count + value / count
        }

        // descriptive variance
        count match {
          case 0 =>
            `var` = 0.0
          case 1 =>
            `var` = (value - avg_1) * (value - avg_1)
          case _ =>
            `var` = ((count - 2) * `var` + (count - 1) * (avg - avg) * (avg_1 - avg) + (value - avg) * (value - avg)) / (count - 1)
        }
      }
  }

  def emit = doNotEmit
}

object GroupByActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json:JValue) = {
    for {
      // from json actor definition
      by <- (json \ "group" \ "by").extractOpt[String]
    } yield {
      by
    }
  }

  def apply(json:JValue):Option[Props] = {
    getParams(json).map(_ =>  Props(classOf[GroupByActor], json))
    // todo: take better care of exceptions and error handling
  }
}

//todo: groupby actor should forward the bead methods to the children
class GroupByActor(json:JObject) extends CoralActor with ActorLogging {

  def jsonDef = json
  val by = GroupByActor.getParams(json).get

  // remove the group section when defining children
  val Diff(_,_,jsonChildrenDef) = jsonDef diff JObject(("group", (json \ "group")))

  //keep the inverted map here?
  //or up in the runtime ?
  var actors = SortedMap.empty[String, Long]

  def state = Map( ("actors", render(actors) ) )

  def emit  = doNotEmit

  def trigger = {
    // todo: group_by support more than a level into dynamic actors tree
    json: JObject =>
      for {
        value   <- getTriggerInputField[String](json \ by)
        count   <- getActorResponse[Long]("/user/coral", GetCount)
      } yield {
        // create if it does not exist
        actorRefFactory.child(value) match
        {
          case Some(actorRef) => actorRef
          case None           => {
            val props = CoralActorFactory.getProps(jsonChildrenDef)
            val actorOpt = props map { p =>
              val actor = actorRefFactory.actorOf(p, s"$value")
              actors += (value -> count)
              tellActor("/user/coral", RegisterActorPath(count, actor.path))
              actor
            }
            // potential exception throwing here!
            actorOpt.get
          }
        }
      } ! json
  }
}

object RestActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json:JValue) = {
    for {
      // from json actor definition
      // possible parameters server/client, url, etc
      t <- (json \ "type").extractOpt[String]
    } yield {
      t
    }
  }

  def apply(json:JValue):Option[Props] = {
    getParams(json).map(_ =>  Props(classOf[RestActor], json))
    // todo: take better care of exceptions and error handling
  }
}


// metrics actor example
class RestActor(json:JObject) extends CoralActor {

  def jsonDef = json

  def state = Map.empty
  def trigger    = noProcess
  def emit       = passThroughEmit
}

object ZscoreActor {
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json:JValue) = {
    for {
    // from json actor definition
    // possible parameters server/client, url, etc
      by    <- (json \ "params" \ "by").extractOpt[String]
      field <- (json \ "params" \ "field").extractOpt[String]
      score <- (json \ "params" \ "score").extractOpt[Double]
    } yield {
      (by, field, score)
    }
  }

  def apply(json:JValue):Option[Props] = {
    getParams(json).map(_ =>  Props(classOf[ZscoreActor], json))
    // todo: take better care of exceptions and error handling
  }

}

// metrics actor example
class ZscoreActor(json:JObject) extends CoralActor {

  def jsonDef = json
  val (by, field, score) = ZscoreActor.getParams(jsonDef).get

  var outlier: Boolean = false
  def state = Map.empty

  def trigger = {
    json: JObject =>
      for {
        // from trigger data
        subpath <- getTriggerInputField[String](json \ by)
        value   <- getTriggerInputField[Double](json \ field)

        // from other actors
        avg     <- getCollectInputField[Double]( "histogram", subpath, "avg")
        std     <- getCollectInputField[Double]( "histogram", subpath, "sd")

        //alternative syntax from other actors multiple fields
        //(avg,std) <- getActorField[Double](s"/user/events/histogram/$city", List("avg", "sd"))
      } yield {
        // compute (local variables & update state)
        val th = avg + score * std
        outlier = value > th
      }
  }

  def emit =
  {
    json: JObject =>

      outlier match {
        case true =>
          // produce emit my results (dataflow)
          // need to define some json schema, maybe that would help
          val result = ("outlier" -> outlier)

          // what about merging with input data?
          val js = render(result) merge json

          //logs the outlier
          log.warning(compact(js))

          //emit resulting json
          js

        case _ => JNothing
      }
  }

}


