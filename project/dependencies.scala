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

    //spray-json (deprecated in favor of json4s)
    "io.spray"           %% "spray-json"     % "1.3.1",

    //json4s
    "org.json4s"         %% "json4s-jackson" % "3.2.11",

    //scalaz
    "org.scalaz"         %% "scalaz-core"    % "7.1.0",

    //akka
    "com.typesafe.akka"  %% "akka-actor"     % akkaVersion,
    "com.typesafe.akka"  %% "akka-slf4j"     % akkaVersion,

    //logging
    "ch.qos.logback"     % "logback-classic" % "1.0.13"
  )

  val allTestDependencies = Seq(
    //spray
    "io.spray"          %% "spray-testkit"   % sprayVersion % "test",

    //akka
    "com.typesafe.akka" %% "akka-testkit"    % akkaVersion % "test",

    //testing
    "org.scalatest"     %% "scalatest"       % "2.2.1" % "test"
  )

  val allDependencies = allBuildDependencies ++ allTestDependencies

}

