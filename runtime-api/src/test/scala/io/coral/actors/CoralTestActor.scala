package io.coral.actors

import akka.actor.{Actor, Props}
import org.json4s.JObject
import org.json4s.JsonAST.{JNothing, JValue}

import scala.concurrent.Future
import scalaz.OptionT

object CoralTestActor {

  def props: Props = Props(classOf[CoralTestActor])

  def props(json: JValue) = Props(classOf[JsonCoralTestActor], json)



}

class CoralTestActor extends CoralActor {

  override def jsonDef: JValue = JNothing

  override def timer: JValue = JNothing

  override def state: Map[String, JValue] = Map.empty[String, JValue]

  override def emit: (JObject) => JValue = _ => JNothing

  override def trigger: (JObject) => OptionT[Future, Unit] = _ => OptionT.none

}

class JsonCoralTestActor(json: JValue) extends CoralTestActor {

  override def jsonDef: JValue = json

}

