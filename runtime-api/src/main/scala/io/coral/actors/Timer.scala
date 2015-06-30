package io.coral.actors

import scala.concurrent.Future
import org.json4s.JsonAST.JValue

trait Timer {
  type TimerType = Future[Option[JValue]]
  def timer: TimerType
}
