/*
 * Copyright 2016 Coral realtime streaming analytics (http://coral-streaming.github.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.coral.api.security

import akka.actor.{Actor, ActorLogging}
import org.json4s.JObject
import scala.collection.mutable
import scala.collection.mutable.{Map => mMap}
import scala.concurrent.Future

case class SignIn(user: User)
case class SendCode(user: User)
case class CheckCode(user: User, provided: String)
case class SignOut(user: User)

/**
  * Handles all messages related to multi-factor authentication.
  * The normal flow is like this:
  *
  * user: calls POST /api/login with his credentials
  * and a JSON object stating: { "step": "request" }
  * server: StatusCodes.Accepted if not already requested, else StatusCodes.BadRequest
  *
  * user: calls POST /api/login again with his credentials
  * and an object stating: { "step": "token", "value": "<code>" }
  * server: StatusCodes.OK if code matches, else StatusCodes.Unauthorized.
  *
  * Constructor for MFAHandler:
  * {
  *    "pushApi": "http://server/push/api"
  * }
  */
class MFAHandler(json: JObject) extends Actor with ActorLogging {
	var pushUrl: Option[String] = None
	//var HttpClientActor = HttpClientActor()

	override def preStart() = {
		//getPushUrl(json)
	}

	// A list of all active sessions
	val activeSessions = mutable.HashSet.empty[User]
	// A list of users to which a code was sent (<user, code>)
	val codeSent = mMap.empty[User, String]

	override def receive = {
		case SignIn(user: User) =>
			//activeSessions += user
		case SendCode(user: User) =>
			//sender ! sendCode(user)
		case CheckCode(user: User, provided: String) =>
			//sender ! checkCode(user, provided)
		case SignOut(user: User) =>
			//activeSessions -= user
		case _ =>
	}

	//def getPushUrl(json: JObject): Option[String] = {
	//	(json \ "pushApi").extractOpt[String]
	//}

	//def sendCode(user: User): Future[JObject] = {
	//	pushUrl match {
	//		case Some(url) =>
	//	}
	//}

	//def checkCode(user: User, provided: String): Future[JObject] = {
	//	codeSent.get
	//}
}