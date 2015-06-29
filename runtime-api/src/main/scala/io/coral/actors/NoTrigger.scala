package io.coral.actors

import org.json4s._

import scala.concurrent.Future

trait NoTrigger extends Trigger {
  override def trigger: TriggerType = json => Future.successful(Some(JNothing))
}
