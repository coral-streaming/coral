import sbt._

object Dependencies {
  val akkaVersion       = "2.3.6"
  val sprayVersion      = "1.3.2"

  val allBuildDependencies = Seq(
    //spray
    "io.spray"           %% "spray-can"       % sprayVersion,
    "io.spray"           %% "spray-io"        % sprayVersion,
    "io.spray"           %% "spray-httpx"     % sprayVersion,
    "io.spray"           %% "spray-routing"   % sprayVersion,
    "io.spray"           %% "spray-client"    % sprayVersion,

    //spray-json (deprecated in favor of json4s)
    "io.spray"           %% "spray-json"     % "1.3.1",

    //json4s
    "org.json4s"         %% "json4s-jackson" % "3.2.11",
    "org.json4s"         %% "json4s-native"  % "3.2.11",

    //scalaz
    "org.scalaz"         %% "scalaz-core"    % "7.1.0",

    //akka
    "com.typesafe.akka"  %% "akka-actor"     % akkaVersion,
    "com.typesafe.akka"  %% "akka-slf4j"     % akkaVersion,

    // metrics
    "io.dropwizard.metrics" % "metrics-graphite" % "3.1.2",
    "nl.grons" %% "metrics-scala" % "3.5.2_a2.3",

    //logging
    "ch.qos.logback"     % "logback-classic" % "1.0.13",

    // cassandra
    "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.4",

    // kafka
    "org.apache.kafka"   %% "kafka" % "0.8.2.1" exclude("org.slf4j", "slf4j-log4j12"),

    // dependency injection
    "org.scaldi" %% "scaldi-akka" % "0.5.3",

    "org.uncommons.maths" % "uncommons-maths" % "1.2.2a"
  )

  val allTestDependencies = Seq(
    //spray
    "io.spray"          %% "spray-testkit"   % sprayVersion % "it,test",

    //akka
    "com.typesafe.akka" %% "akka-testkit"    % akkaVersion % "it,test",

    // cassandra
    "org.cassandraunit" % "cassandra-unit"   % "2.1.3.1" % "it,test" exclude("org.slf4j", "slf4j-log4j12"),

    //testing
    "org.scalatest"     %% "scalatest"       % "2.2.1" % "it,test"
  )

  val allDependencies = allBuildDependencies ++ allTestDependencies
}