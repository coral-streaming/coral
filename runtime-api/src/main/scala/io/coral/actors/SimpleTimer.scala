package io.coral.actors

import org.json4s.JsonAST.{JValue, JObject}

import scala.concurrent.Future

trait SimpleTimer extends Timer {
  override def timer: TimerType = {
    Future.successful(simpleTimer)
  }

  def simpleTimer: Option[JValue]
}
