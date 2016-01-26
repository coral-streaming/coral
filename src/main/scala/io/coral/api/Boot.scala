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

package io.coral.api

import java.io.File
import java.util.UUID
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.slf4j.Logger
import akka.io.IO
import akka.util.Timeout
import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import io.coral.actors.CoralActor.Shunt
import io.coral.actors.RootActor._
import io.coral.actors.RootActor
import io.coral.actors.database.{DatabaseStatements, CassandraActor}
import io.coral.api.security._
import io.coral.utils.Utils
import org.json4s.JsonAST.JObject
import scopt.OptionParser
import spray.can.Http
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.collection.JavaConverters._
import com.github.t3hnar.bcrypt._
import scala.concurrent.duration._

object Boot extends App {
	val coralVersion = "0.0.113"

	/**
	 * Parses command line arguments given to the application
	 * and then determines what to do with them.
	 */
	def run(args: Array[String]) {
		createCommandLineParser().parse(args, CommandLineConfig()).map { config =>
			config.cmd match {
				case "start" =>
					startCoral(config)
				case "stop" =>
					stopCoral()
				case "user" =>
					handleUserCommands(config)
				case "version" =>
					showVersion()
				case "help" =>
					createCommandLineParser().showUsage
					System.exit(0)
			}
		}
	}

	/**
	 * Handles user commands, such as adding and removing users
	 * from the coral platform.
	 */
	def handleUserCommands(config: CommandLineConfig) {
		config.subcommand match {
			case Some("add") =>
				addUser(config)
			case Some("remove") =>
				// removeUser(config)
			case Some("help") =>
				createCommandLineParser().showUsage
				System.exit(0)
			case _ =>
		}
	}

	/**
	 * Shows the version of the Coral Platform and then exits.
	 */
	def showVersion() {
		println(s"Coral Platform - version $coralVersion.")
		System.exit(0)
	}

	/**
	 * Prints the help message to the command line. This uses println on purpose
	 * since it will always show, regardless of the loggers are set up properly.
	 */
	def startCoral(commandLineConfig: CommandLineConfig): (ActorSystem, ActorRef) = {
		implicit val timeout = Timeout(5.seconds)

		try {
			// Combine command line, given config and default config
			implicit val config = getFinalConfig(commandLineConfig)

			// Create the actor system
			implicit val system = ActorSystem("coral", config.getConfig)

			// Create the root actor
			implicit val root = system.actorOf(Props(new RootActor()(
				new DefaultModule(system.settings.config), global, config)), "root")

			// Prepare and check the Cassandra database
			initializeDatabase()

			// Prepare all helper actors and the HTTP API actor
			val service = Await.result(root.ask(CreateHelperActors()),
				timeout.duration).asInstanceOf[ActorRef]

			// Bind the API actor to the HTTP interface
			bindHTTP(service)

			(system, service)
		} catch {
			case e: Exception =>
				Utils.printFatalError(e)
				(null, null)
		}
	}

	/**
	 * Bind the HTTP actor to the HTTP interface that is specified in the Coral configuration.
	 * @param serviceActor The Spray HTTP API actor that is supposed to handle the HTTP traffic.
	 * @param config The CoralConfig object from which to get the coral.api.interface
	 *               and coral.api.port values.
	 * @param timeout The timeout after which a failure is thrown.
	 */
	def bindHTTP(serviceActor: ActorRef)(implicit config: CoralConfig, system: ActorSystem, timeout: Timeout) {
		// fetch configuration from resources/application.config
		val interface = config.getConfig.getString("coral.api.interface")
		val port = config.getConfig.getInt("coral.api.port")

		// start a new HTTP server with our service actor as the handler
		val future = (IO(Http) ? Http.Bind(serviceActor, interface, port)).map {
			case _: Http.Bound => true
			case _ => false
		}

		// Have to wait before interface is bound
		val bound = Await.result(future, timeout.duration)

		if (!bound) {
			throw new Exception("Failed to bind HTTP interface.")
		}
	}

	def stopCoral() {
		// TODO: Find the process, kill it?
		// TODO: Exit the cluster first?
	}

	/**
	 * Combine the command line parameters, the optional given configuration file through
	 * the command line and the default configuration file into a single CoralConfig object.
	 * @param commandLine The command line options passed to the program at startup.
	 * @return An object combining the command line parameters, the given configuration file
	 *         and the default configuration file. The command line overrides the given configuration
	 *         file which overrides the default configuration file.
	 */
	def getFinalConfig(commandLine: CommandLineConfig): CoralConfig = {
		val fileConfig = commandLine.config match {
			case None =>
				println(s"""No configuration file specified on command line, """
					+ """loading default application.conf""")
				new CoralConfig(ConfigFactory.load())
			case Some(path) =>
				// Load the configuration file with the given name,
				// and use the default as a fallback.

				// This can be an absolute path, or it is a path relative from where
				// the "./coral" command was given.
				val file = getFile(path)

				println(s"""Using configuration file with path "${file.getAbsolutePath()}" and using """
					+ """default application.conf as fallback.""")

				if (!file.exists()) {
					println(s"""Warning: file "${file.getAbsolutePath}" does not exist, """ +
					   "using default application.conf.")
				}

				val parsed = ConfigFactory.parseFile(file)
				new CoralConfig(parsed.withFallback(ConfigFactory.load()))
		}

		val enableCluster = !commandLine.noCluster.getOrElse(!fileConfig.coral.cluster.enabled)

		// Try to get the parameters from the command line
		// or else fill them in from the fileConfig above
		val str = s"""
		   |akka {
		   |  remote {
		   |    netty.tcp {
		   |	  hostname = ${commandLine.akkaHostname.getOrElse(fileConfig.akka.remote.nettyHostname)}
		   |      port = ${commandLine.akkaPort.getOrElse(fileConfig.akka.remote.nettyTcpPort)}
   		   |    }
		   |  }
		   |
		   |  actor {
		   |    provider = "akka.cluster.ClusterActorRefProvider"
		   |  }
		   |
		   |  cluster {
		   |    seed-nodes = ${createConfigStringList(commandLine.seedNodes,
					fileConfig.getConfig.getStringList("coral.cluster.seed-nodes").asScala.toList)}
		   |  }
		   |}
		   |
		   |coral {
		   |  log-level = ${commandLine.logLevel.getOrElse(
					fileConfig.coral.logLevel).toUpperCase}
		   |  api {
		   |    interface = "${commandLine.apiInterface.getOrElse(
					fileConfig.coral.api.interface)}"
		   |    port = ${commandLine.apiPort.getOrElse(
					fileConfig.coral.api.port)}
		   |  }
		   |
		   |  authentication {
		   |    mode = "${commandLine.authenticationMode.getOrElse(
					fileConfig.coral.authentication.mode)}"
		   |  }
		   |
		   |  cluster {
		   |    enable = ${enableCluster}
		   |  }
		   |
		   |  cassandra {
		   |    contact-points = ${createConfigStringList(commandLine.cassandraContactPoints,
					fileConfig.getConfig.getStringList("coral.cassandra.contact-points").asScala.toList)}
		   |    port = ${commandLine.cassandraPort.getOrElse(
					fileConfig.coral.cassandra.port)}
		   |    keyspace = "${commandLine.cassandraKeyspace.getOrElse(
					fileConfig.coral.cassandra.keyspace)}"
		   |  }
		   |}""".stripMargin

		val result = new CoralConfig(ConfigFactory.parseString(str)
			.withFallback(fileConfig.getConfig))

		// In the case that the no cluster flag is provided, the seed node settings are ignored
		// and the node is started up in single node mode.
		//val finalResult = if (!result.coral.cluster.enabled) {
		//	new CoralConfig(result.getConfig.withoutPath("akka.cluster.seed-nodes"))
		//} else {
		//	result
		//}

		Utils.setPersistenceSystemProps(result)
		result
	}

	/**
	 * Get a configuration file from a given path string.
	 * The configuration file can be relative, e.g. "conf/application.conf"
	 * or absolute "/home/user/application.conf". In both cases, this
	 * method returns the final, full path of the configuration file.
	 * @param filename The name of the file to get
	 * @return A file object representing the file.
	 */
	def getFile(filename: String): File = {
		if (!new File(filename).isAbsolute()) {
			new File(System.getProperty("user.dir") + "/" + filename)
		} else {
			new File(filename)
		}
	}

	def createConfigStringList(list: Option[List[String]], fallback: List[String]): String = {
		val result = (list match {
			// No list given with command line, parse the defaults
			case None => fallback
			case Some(l) => if (!l.isEmpty) l else fallback
		}).mkString("[\"", "\", \"", "\"]")

		result
	}

	/**
	 * Properly initialize the database. Creates the tables that are required
	 * for the program to run, if they do not exist yet.
	 * If the keyspace exists and tables in it also exist, these
	 * tables are assumed to be of the correct layout and are not recreated or deleted.
	 *
	 * NOTE: This is NOT the final Cassandra actor. The actor that checks/creates the database
	 * is just temporary, the CassandraActor that handles actual queries is different from
	 * the one created here.
	 *
	 * @param config The configuration file to read the keyspace name and table names from.
	 */
	def initializeDatabase()(implicit system: ActorSystem, config: CoralConfig) {
		// Create a temporary cassandra actor that connects to the system keyspace
		val conf = new CoralConfig(ConfigFactory.parseString(
			"coral.cassandra.keyspace = system").withFallback(config.getConfig))
		val coralConfig = CassandraActor.fromConfig(conf)

		val systemCassandra = system.actorOf(coralConfig)

		implicit val timeout = Timeout(10.seconds)
		
		DatabaseStatements.initializeDatabase(config).foreach(stmt => {
			// Has to be blocking, cannot continue if database is not initialized
			val answer = Await.result(systemCassandra.ask(
				Shunt(parse(stmt.replaceAll("\n", "")).asInstanceOf[JObject])),
				timeout.duration).asInstanceOf[JObject]

			implicit val formats = org.json4s.DefaultFormats
			val success = (answer \ "success").extract[Boolean]

			if (!success) {
				throw new Exception(s"Database creation failed: ${compact(render(answer))}")
			}
		})
	}

	/**
	 * Create a command line parser that parses all command line options and checks
	 * whether they are valid. Also prints usage info when asked.
	 * @return The OptionParser for the command line parameters.
	 */
	def createCommandLineParser(): OptionParser[CommandLineConfig] = {
		new scopt.OptionParser[CommandLineConfig]("coral") {
			/**=== Start and stop options ===**/
			opt[Unit]("version") abbr("v") text("Shows version information for the platform.") action {
				(_, c) => c.copy(cmd = "version") }
			help("help") text("Prints this message.\n") abbr("h") action {
				(_, c) => c.copy(cmd = "help") }
			cmd("start") text("Starts the Coral Platform on this machine.") action {
				(_, c) => c.copy(cmd = "start") } children(
				opt[String]("configfile") abbr("c") optional()
					text ("(Optional) The .conf configuration file to use.\n\tCommand line parameters override "
					+ "configuration settings\n\twith the same name as in this file,\n\twhich override "
					+ "the default application.conf in turn.") action {
					(x, c) => c.copy(cmd = "start", config = Some(x)) },
				opt[String]("apiinterface") optional() abbr("ai")
					text("(Optional) The interface that the HTTP API listens to.") action {
					(x, c) => c.copy(apiInterface = Some(x))},
				opt[Int]("apiport") optional() abbr("p")
					text("(Optional) The port that the HTTP API listens on.") action {
					(x, c) => c.copy(apiPort = Some(x)) },
				opt[String]("akkahostname") optional() abbr("ah")
					text("(Optional) The interface that akka listens on.") action {
					(x, c) => c.copy(akkaHostname = Some(x)) },
				opt[Int]("akkaport") optional() abbr("ap")
					text("(Optional) The port that akka uses for actor communication.") action {
					(x, c) => c.copy(akkaPort = Some(x)) },
				opt[String]("authenticationmode") optional() abbr("am")
					text("(Optional) The authentication mode to use. Can be\n\t'accept-all', "
						+ "'deny-all', 'coral' or 'ldap'.")
					action { (x, c) => c.copy(authenticationMode = Some(x))},
				opt[String]("contactpoints") abbr("ccp") optional()
					text("(Optional) A list of ip addresses of cassandra seed\n\tnodes, "
						+ "separated by a comma.\n\tFor example: \"192.168.0.1, 192.168.0.2\"") action {
					(x, c) => c.copy(cassandraContactPoints = Some(x.split(",").toList)) },
				opt[Int]("cassandraport") optional() abbr("cp")
					text("(Optional) The port to connect to cassandra.") action {
					(x, c) => c.copy(cassandraPort = Some(x))},
				opt[String]("keyspace") optional() abbr("k")
					text("(Optional) The cassandra keyspace to connect with.") action {
					(x, c) => c.copy(cassandraKeyspace = Some(x))},
				opt[Unit]("nocluster") optional() abbr("nc")
					text("(Optional) Run the node without attaching it to a cluster.") action {
					(_, c) => c.copy(noCluster = Some(true)) },
				opt[String]("seednodes") abbr("sn") optional()
					text("(Optional) A list of akka seed nodes to connect to.\n"
						+ "\tThese must have the format \"akka.tcp://coral@192.168.0.1:2551\"\n"
						+ "\twhere '192.168.0.1' is the IP address of the akka seed node and\n"
						+ "\t2551 the port that the seed node listens to.") action {
						(x, c) => c.copy(seedNodes = Some(x.split(",").toList))},
				opt[String]("loglevel") abbr("ll")
					text("(Optional) Sets the log level for the Coral platform.\n") action {
					(x, c) => c.copy(logLevel = Some(x))}
			)
			/**=== User commands ===**/
			cmd("user") text("Adds and removes users from the platform. " +
				"\nType \"user -help\" for more information.") action {
				(_, c) => c.copy(cmd = "user") } children(
					cmd("add") text("Add a user to the platform.") action {
						(_, c) => c.copy(subcommand = Some("add")) } children(
						opt[String]("contactpoints") abbr("ccp") required()
							text("A list of ip addresses of cassandra seed\n\tnodes, "
							+ "separated by a comma.\n\tFor example: \"192.168.0.1, 192.168.0.2\"") action {
							(x, c) => c.copy(cassandraContactPoints = Some(x.split(",").toList)) },
						opt[Int]("cassandraport") abbr("cp") required()
							text("The port to connect to cassandra.") action {
							(x, c) => c.copy(cassandraPort = Some(x))},
						opt[String]("keyspace") abbr("k") required()
							text("The cassandra keyspace to connect with.") action {
							(x, c) => c.copy(cassandraKeyspace = Some(x))},
						opt[String]("uniquename") abbr("n") required()
							text("The unique name of the user. This is the login name of the user.") action {
							(x, c) => c.copy(uniqueName = Some(x)) },
						opt[String]("fullname") abbr("f") required()
							text("The friendly, full name (first and last) of the user.") action {
							(x, c) => c.copy(fullName = Some(x)) },
						opt[String]("email") abbr("e") required()
							text("The e-mail address of the user.") action {
							(x, c) => c.copy(email = Some(x)) },
						opt[String]("mobile") abbr("m") optional()
							text("The mobile phone number of the user (e.g. \"+31612345678\")") action {
							(x, c) => c.copy(mobilePhone = Some(x)) },
						opt[String]("department") abbr("d") required()
							text("The department of the user.\n") action {
							(x, c) => c.copy(department = Some(x)) }
					),
					cmd("remove") text("Remove a user from the platform.") action {
						(_, c) => c.copy(subcommand = Some("remove")) } children(
						opt[String]("uniquename") abbr("n")
							text("The unique name of the user. This is the login name of the user.") action {
							(x, c) => c.copy(uniqueName = Some(x))}))
		}
	}

	/**
	 * Adds an admin user through the command line interface.
	 * This is needed to log in for the first time on the API.
	 * Other users after this can be added with the API itself.
	 */
	def addUser(command: CommandLineConfig) {
		val password = new String(System.console().readPassword("Enter password for the user:"))
		val reenter = new String(System.console().readPassword("Confirm password:"))

		try {
			if (password != reenter)
				throw new Exception("Passwords do not match.")
			if (!checkPassword(password, command.uniqueName.get))
				throw new Exception("Password does not meet the security requirements.")
			if (!checkEmail(command.email))
				throw new Exception("Email is invalid.")

			val hashed = password.bcrypt(generateSalt)

			val user = User(
				UUID.randomUUID(),
				// Gets are OK here since scopt enforces that these are defined
				// Department and mobile phone are optional
				command.fullName.get,
				command.department,
				command.email.get,
				command.uniqueName.get,
				command.mobilePhone,
				hashed,
				System.currentTimeMillis,
				None)

			val system = ActorSystem("userSystem",
				ConfigFactory.load()
					.withValue("akka.actor.provider",
					ConfigValueFactory.fromAnyRef("akka.actor.LocalActorRefProvider")))

			val ccpCleaned = Utils.cleanContactPointList(command.cassandraContactPoints.get)
			val contactPoints = ccpCleaned.mkString("\"", "\",\"", "\"")

			implicit val timeout = Timeout(5.seconds)
			implicit val formats = org.json4s.DefaultFormats

			val root = system.actorOf(Props(new RootActor()(
				new DefaultModule(system.settings.config), global,
				new CoralConfig(system.settings.config))), "root")

			val json = parse(s"""{
				| "params": {
				|   "seeds": [${contactPoints}],
				|   "keyspace": "${command.cassandraKeyspace.get}",
				|   "port": ${command.cassandraPort.get}
				|}}""".stripMargin).asInstanceOf[JObject]
			val cassandra = Await.result(root.ask(CreateCassandraActor(Some(json))), timeout.duration)

			val permissionHandler = system.actorOf(Props(new PermissionHandler()), "permissionHandler")

			// Will have to wait here otherwise application exists immediately
			val answer = Await.result(permissionHandler.ask(AddAdmin(user)),
				timeout.duration).asInstanceOf[JObject]
			val success = (answer \ "success").extract[Boolean]

			success match {
				case true =>
					Logger(getClass.getName).info("Succesfully added user to database.")
					System.exit(0)
				case false =>
					val reason = (answer \ "reason").extract[String]
					Logger(getClass.getName).error("Failed to add user to database. Reason: " + reason)
					Utils.printFatalError(new Exception(reason))
					System.exit(1)
			}
		} catch {
			case e: Exception =>
				Utils.printFatalError(new Exception(e.getMessage))
				System.exit(1)
		}
	}

	/**
	 * Checks the provided password against a set of password checks.
	 * @param password The password provided.
	 * @param uniqueName The unique name of the user. Mainly there to make
	 *                   sure the user name is not equal to or part of the password.
	 * @return True when valid password, false otherwise.
	 */
	def checkPassword(password: String, uniqueName: String): Boolean = {
		type pwdCheck = (String) => Boolean

		def minimumLength: pwdCheck = _.length >= 6
		def atLeastOneDigit: pwdCheck = _.matches("\\d")
		def atLeastOneCapital: pwdCheck = _.matches("\\D")
		def notSameAsUsername: pwdCheck = _ != uniqueName
		def multipleCharacters: pwdCheck = _.groupBy(c => c.toLower).toList.size >= 3

		// TODO: Add password checks here
		val criteria: List[pwdCheck] = List()
		criteria.forall(_(password))
	}

	/**
	 * Checks the format of a valid email address.
	 * @param email The supposed email address provided. If None is provided,
	 *              false is returned.
	 * @return True when the string is a valid email address.
	 */
	def checkEmail(email: Option[String]): Boolean = {
		// See https://www.w3.org/TR/html-markup/datatypes.html#form.data.emailaddress
		val emailRegex = "/^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$/"

		email match {
			case None => false
			case Some(e) => e.matches(emailRegex)
		}

		true
	}

	run(args)
}