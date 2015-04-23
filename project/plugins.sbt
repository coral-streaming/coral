scalaVersion := "2.10.5"

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Spray Repository"    at "http://repo.spray.io"
)

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M1")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.4")


