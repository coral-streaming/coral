package io.coral.actors.transform

/**
 * Created by Hoda Alemi on 3/19/15.
 */

import akka.actor.ActorLogging
import io.coral.actors.CoralActor
import io.coral.lib.Tree
import org.json4s.{JObject, _}

object SortingActor{
  implicit val formats = org.json4s.DefaultFormats

  def getParams(json: JValue) = {
    for {
      field <- (json \ "params" \ "field").extractOpt[String]
    } yield {
      field
    }
  }
}

class SortingActor(json: JObject) extends CoralActor with ActorLogging {
  val field = SortingActor.getParams(json).get
  val tree: Tree[Float] = new Tree[Float]()
  //render(List(0.0f,2f))
  def jsonDef = json

  def timer = notSet

  def state= {
    null
    //Map("sortedTree", JNull)  //tree.preorder
  }

  def emit = doNotEmit

  def trigger ={
    json: JObject =>
      for {
        value <- getTriggerInputField[Float](json \ field)
      } yield {
        tree.add(value)
      }
  }
}
