scalaVersion := "2.10.5"

resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Spray Repository"    at "http://repo.spray.io"
)

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M1")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.1.0")

// the following plugin is built from source locally
// using sources from a sbt-coveralls fork at git://github.com/coral-streaming/sbt-coveralls
// check project/project/build.scala and http://www.scala-sbt.org/0.13.5/docs/Extending/Plugins.html 1d)
//
// addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.0.0")
