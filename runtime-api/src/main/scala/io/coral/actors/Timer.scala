package io.coral.actors

import org.json4s._

import scala.concurrent.Future
import org.json4s.JsonAST.JValue

trait Timer {
  type TimerType = Future[Option[JValue]]
  def timer: TimerType
}

trait SimpleTimer extends Timer {
  override def timer: TimerType = {
    Future.successful(simpleTimer)
  }

  def simpleTimer: Option[JValue]
}

trait NoTimer extends Timer {
  override def timer: TimerType = Future.successful(Some(JNothing))
}

