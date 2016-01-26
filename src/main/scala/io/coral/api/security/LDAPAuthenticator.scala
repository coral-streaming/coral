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

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.{Certificate, CertificateFactory}
import java.util.UUID
import javax.net.ssl.TrustManagerFactory
import akka.actor.{ActorLogging, ActorSelection}
import akka.event.slf4j.Logger
import akka.util.Timeout
import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.SSLUtil
import io.coral.api.CoralConfig
import io.coral.api.security.Authenticator.InvalidationComplete
import spray.routing.authentication._
import spray.routing.directives.AuthMagnet
import scala.concurrent.{Await, Future, ExecutionContext}
import akka.pattern.ask
import scala.concurrent.duration._
import com.github.t3hnar.bcrypt._

object LDAPAuthenticator {
	case class Authenticate(userPass: Option[UserPass])
	case class FromServerAndStore(owner: String)

	def ldapAuthenticator(ldapActor: ActorSelection)(
		implicit ec: ExecutionContext): AuthMagnet[AuthInfo] = {
		def validateUser(userPass: Option[UserPass]): Future[Option[AuthInfo]] = {
			implicit val timeout = Timeout(10.seconds)
			Logger(getClass.getName).info("Validating user with LDAPAuthenticator")
			ldapActor.ask(LDAPAuthenticator.Authenticate(userPass))
				.asInstanceOf[Future[Option[AuthInfo]]]
		}

		def authenticator(userPass: Option[UserPass]): Future[Option[AuthInfo]] = {
			validateUser(userPass)
		}

		BasicAuth(authenticator _, realm = "Coral")
	}
}

/**
 * An authenticator that performs authentication against an LDAP server.
 * What's special about this authenticator in comparison to other authenticators is the following:
 * - LDAP knows nothing about UUID's that Coral generates, but this authenticator must
 *   act like all other authenticators in response to queries from the system;
 * - It is expensive to authenticate against LDAP, therefore, requests are
 *   cached as much as possible in this actor;
 * - The permission objects are still stored in Coral and need to be fetched separately,
 *   and also need to be cached so that they do not need to be fetched with every request.
 */
class 	LDAPAuthenticator(config: CoralConfig)(implicit ec: ExecutionContext)
	extends CoralAuthenticator(config) with ActorLogging {
	val salt = generateSalt
	var ldapConn: Option[LDAPConnection] = None

	override def preStart() = {
		ldapConn = createConnection()
		refreshRuntimeTable()

		// In case of a crash, LDAP always starts with an empty user map
		users = Map.empty[String, AuthInfo]
	}

	override def receive = ldapReceive orElse super.receive

	def ldapReceive: Receive = {
		case LDAPAuthenticator.Authenticate(userPass: Option[UserPass]) =>
			authenticate(userPass)
		case Authenticator.GetUserUUIDFromUniqueName(uniqueName: String) =>
			getUserUUIDFromUniqueName(uniqueName)
	}

	override def authenticate(userPass: Option[UserPass]) = {
		val originalSender = sender()

		userPass match {
			case None =>
				log.error("Cannot authenticate with nonexisting user and password")
				originalSender ! None
			case Some(up) =>
				ldapConn match {
					case None =>
						log.error("Cannot authenticate on nonexisting LDAP connection")
						originalSender ! None
					case Some(conn) =>
						originalSender ! authenticate(up, conn)
				}
		}
	}

	override def getUserUUIDFromUniqueName(uniqueName: String): Option[UUID] = {
		val originalSender = sender()

		if (users.contains(uniqueName)) {
			log.info("Returning cached user UUID")
			val answer = Some(users(uniqueName).id)
			originalSender ! answer
			answer
		} else {
			log.info(s"Cached user map does not contain user '${uniqueName}'")
			val result: Option[AuthInfo] = fromServerAndStore(uniqueName)

			result match {
				case None =>
					originalSender ! None
					None
				case Some(authInfo) =>
					// This can only be Left here, so get is safe
					val id = Some(authInfo.user.left.get.id)
					log.info(s"Obtained and stored id ${id.toString} from LDAP.")
					originalSender ! id
					id
			}
		}
	}

	/**
	 * Invalidates the LDAPAuthenticator. This overrides the default invalidation behavior
	 * because the cached user map needs to be refreshed.
	 */
	override def invalidate() {
		val originalSender = sender()
		refreshRuntimeTable()
		refreshPermissions()
		originalSender ! InvalidationComplete()
	}

	/**
	 * Get an AuthInfo object based on a unique user name.
	 * @param uniqueUserName The unique name to obtain an AuthInfo object for
	 * @return An optional AuthInfo object if the user was found, None otherwise.
	 */
	override def getAuthInfo(uniqueUserName: String): Option[AuthInfo] = {
		users.get(uniqueUserName) orElse fromServerAndStore(uniqueUserName) orElse None
	}

	/**
	 * LDAP cannot return users based on a UUID because this information
	 * is not stored on the LDAP server but created on the Coral platform,
	 * where UUIDs are assigned when a user is created. Since LDAP users are
	 * never created on the platform but fetched from LDAP,
	 * LDAP does not know about these UUIDs.
	 */
	override def getAuthInfo(userId: UUID): Option[AuthInfo] = {
		users.find(_._2.id == userId).map(_._2)
	}

	/**
	 * Get the authInfo from the server based on the user/password combination.
	 * @param up The user/password combination straight from the HTTP header.
	 * @return An AuthInfo object matching the user/password combination, if any.
	 */
	override def getAuthInfo(up: UserPass): Option[AuthInfo] = {
		users.get(up.user) orElse fromServerAndStore(up.user) orElse None
	}

	/**
	 * Create a connection with an LDAP server based on the
	 * certificates, hostname, port and bindDSN as specified in the
	 * configuration file. This assumes that the LDAP server is secured with SSL.
	 * @return Some(connection) if the connection succeeded, None otherwise.
	 */
	def createConnection(): Option[LDAPConnection] = {
		try {
			log.info("Connecting to LDAP server...")

			val connection = if (config.coral.authentication.ldap.certificates.length == 0) {
				// Unsecured connection without certificates
				new LDAPConnection()
			} else {
				// Secured connection with certificates
				val ts = KeyStore.getInstance(KeyStore.getDefaultType())
				ts.load(null)

				config.coral.authentication.ldap.certificates.foreach(c => {
					getCertificate(c).map(ts.setCertificateEntry(c, _))
				})

				val tmf = TrustManagerFactory.getInstance("X509")
				tmf.init(ts)
				val trustManagers = tmf.getTrustManagers()
				val sslUtil = new SSLUtil(trustManagers)
				val socketFactory = sslUtil.createSSLSocketFactory()
				new LDAPConnection(socketFactory)
			}

			connection.connect(
				config.coral.authentication.ldap.host,
				config.coral.authentication.ldap.port)

			if (connection.isConnected) {
				log.info("Connection to LDAP server succeeded.")
				Some(connection)
			} else {
				log.error("Connection to LDAP server failed.")
				None
			}
		} catch {
			case e: Exception =>
				log.error(e.getMessage())
				None
		}
	}

	/**
	 * Obtain an X.509 certificate from the given filename.
	 * @param filename The full path to the file of the certificate (".crt")
	 * @return A certificate from that file.
	 */
	def getCertificate(filename: String): Option[Certificate] = {
		try {
			val cf = CertificateFactory.getInstance("X.509")
			val file = getClass.getResource(filename).getFile
			val fin = new FileInputStream(file)
			val result = cf.generateCertificate(fin)
			Some(result)
		} catch {
			case e: Exception =>
				log.error(e.getMessage())
				None
		}
	}

	/**
	 * Authenticate a user based on the given LDAP connection.
	 * This just checks whether a user with the given cn and dc's is
	 * present in the LDAP directory and the password matches.
	 * It uses the bindDN property from the .conf properties file.
	 * The bindDN should have the following format:
	 *
	 * "cn={user},ou=<ou>,ou=<ou>,dc=<dc>,dc=<dc>"
	 *
	 * The {user} field will be filled in by this method to be the
	 * user name that the user has specified in the HTTPS Basic Auth header.
	 * Example:
	 * "cn={user},ou=marketing,ou=europe,dc=company,dc=com"
	 *
	 * A new AuthInfo object is returned containing the user information
	 * fetched from LDAP and a list of permissions, fetched from Cassandra.
	 *
	 * @param up The user/password combination that was given in the HTTPS Basic Auth
	 *           header that will be used to authenticate the user with.
	 * @param conn The LDAPConnection (secured with SSL) that will be used
	 *             to verify the user against.
	 * @return A Future[Option[AuthInfo]] object. The other part of the AuthInfo
	 *         object (namely the list of permissions) is not fetched from
	 *         LDAP but instead from the internal Coral database.
	 */
	def authenticate(up: UserPass, conn: LDAPConnection): Option[AuthInfo] = {
		if (users.contains(up.user)) {
			log.info("Obtaining user from Coral cache.")

			users.get(up.user) match {
				case None => None
				case Some(u) =>
					u.user match {
						case Left(x) if (x.passwordMatches(up.pass)) => Some(u)
						case Right(all) => Some(u)
						case _ => None
					}
			}
		} else {
			try {
				log.info("Authenticating user with LDAP...")
				val bindDN = config.coral.authentication.ldap.bindDN
				val bindRequest = new SimpleBindRequest(
					bindDN.replace("{user}", up.user), up.pass)
				bindRequest.setResponseTimeoutMillis(500)
				val bindResult = conn.bind(bindRequest)
				val success: Boolean = (bindResult.getResultCode == ResultCode.SUCCESS)

				if (success) {
					log.info("Authentication with LDAP succeeded.")
					fromServerAndStore(up.user)
				} else {
					log.error("Authentication with LDAP failed.")
					None
				}
			} catch {
				case e: Exception =>
					log.error(e.getMessage)
					None
			}
		}
	}

	/**
	 * Create an AuthInfo object from a unique user name.
	 * The user name must be looked up in LDAP and the AuthInfo object
	 * will be filled from the LDAP entry.
	 * @param uniqueName The unique name of the user
	 * @return Some(authInfo) if the user can be found in LDAP, None otherwise.
	 */
	def fromServerAndStore(uniqueName: String): Option[AuthInfo] = {
		log.info(s"Fetching user '$uniqueName' from LDAP server...")
		val user: Option[User] = getUserFromServer(uniqueName)

		user match {
			case None => None
			case Some(u) =>
				val permissions: List[Permission] = getPermissionsFromDatabase(u.id)
				log.info(s"Obtained a list of ${permissions.size} permissions for LDAP user '${uniqueName}'.")
				val authInfo = AuthInfo(Left(u), permissions)

				log.info(s"Caching user ${authInfo.uniqueName}.")
				users += (uniqueName -> authInfo)

				Some(authInfo)
		}
	}

	/**
	 * Returns a user with a unique name from the LDAP server.
	 * @param uniqueName The unique name of the user.
	 * @return If found, Some(user) with uniqueName equal to the parameter given.
	 */
	def getUserFromServer(uniqueName: String): Option[User] = {
		val user = config.coral.authentication.ldap.bindDN.replace("{user}", uniqueName)
		val searchResult = ldapConn.get.search(
			user,
			SearchScope.BASE,
			s"(uid=$uniqueName)",
			"fullName",
			"department",
			"email",
			"mobile",
			"userPassword",
			// created on
			System.currentTimeMillis.toString,
			// last login
			System.currentTimeMillis.toString
		)

		searchResult.getEntryCount match {
			case 0 =>
				log.error(s"""LDAP search returned no results for user '$uniqueName'""")
				None
			case 1 =>
				val entry: SearchResultEntry = searchResult.getSearchEntries.get(0)

				try {
					val uuid = UUID.randomUUID

					// TODO: this is not designed as it should be
					// The fields with "get" should be present in the LDAP directory.
					// What happens if they are not present?
					// Why is userPassword not present in the directory?
					Some(User(
						uuid,
						getOptionalEntry(entry, "fullName").get,
						getOptionalEntry(entry, "department"),
						getOptionalEntry(entry, "email").get,
						uniqueName,
						getOptionalEntry(entry, "mobile"),
						getOptionalEntry(entry, "userPassword").get.bcrypt(salt),
						toLongOpt(getOptionalEntry(entry, "createdOn")).getOrElse(0L),
						toLongOpt(getOptionalEntry(entry, "lastLogin"))))
				} catch {
					case e: Exception =>
						log.error(e.getMessage)
						None
				}
			case n =>
				log.error(s"""LDAP search returned more than one result for user "$uniqueName"""")
				None
		}
	}

	/**
	 * Obtain an optional entry from an LDAP entry.
	 * @param entry The entry to get an attribute from.
	 * @param field The name of the LDAP attribute.
	 * @return Some(attribute) with the value of the attribute if found, None otherwise.
	 */
	def getOptionalEntry(entry: Entry, field: String): Option[String] = {
		if (entry.hasAttribute(field)) {
			Some(entry.getAttribute(field).getValue)
		} else {
			None
		}
	}

	/**
	 * Tries to convert an Option[String] into an Option[Long] if
	 * the string can be parsed to a long.
	 * @param value The string value to parse
	 * @return Some(long) or if parsing failed None.
	 */
	def toLongOpt(value: Option[String]): Option[Long] = {
		value match {
			case None => None
			case Some(v) =>
				try {
					Some(v.toLong)
				} catch {
					case _: Exception => None
				}
		}
	}
}