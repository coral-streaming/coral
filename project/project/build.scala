import sbt._
import Keys._

object ProjectBuild extends Build {
  override lazy val projects = Seq(root)

  val projectSettings = Defaults.coreDefaultSettings ++ Seq(
    scalacOptions := Seq("-deprecation", "-feature", "-encoding", "utf8")
  )

  lazy val root = Project(
    id = "project",
    base = file("."),
    settings = projectSettings).dependsOn(RootProject(coverallsPlugin))

  lazy val coverallsPlugin = uri("git://github.com/coral-streaming/sbt-coveralls")
}

