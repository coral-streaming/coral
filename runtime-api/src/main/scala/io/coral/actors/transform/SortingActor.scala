package io.coral.actors.transform

/**
 * Created by Hoda Alemi on 3/19/15.
 */

import akka.actor.{ActorLogging, Props}
import io.coral.actors.CoralActor
import org.json4s.JObject
import org.json4s.JsonAST.{JArray, JValue}
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods.render

import scala.concurrent.Future
import scalaz.OptionT
import io.coral.lib.Tree

object SortingActor{
  implicit val formats = org.json4s.DefaultFormats
}

class SortingActor(json: JObject) extends CoralActor with ActorLogging {

  val tree: Tree[Float] = new Tree[Float]()


  def jsonDef = json

  def timer = notSet

  def state = Map("sortedTree", render(tree.preorder))

  def emit = doNotEmit

  def trigger: (JObject) =>
}
