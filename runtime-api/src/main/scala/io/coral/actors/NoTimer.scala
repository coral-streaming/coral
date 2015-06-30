package io.coral.actors

import org.json4s._

import scala.concurrent.Future

trait NoTimer extends Timer {
  override def timer: TimerType = Future.successful(Some(JNothing))
}
