import sbt._
import Keys._

object ProjectBuild extends Build {
  override lazy val projects = Seq(root)

  val projectSettings = Defaults.coreDefaultSettings ++ Seq(
    scalacOptions := Seq("-deprecation", "-feature", "-encoding", "utf8")
  )

  lazy val project = Project(
    id = "project",
    base = file("."),
    settings = projectSettings)

  lazy val root = sys.props.get("coveralls") match {
    case Some(_) =>
      val coverallsPlugin = uri("git://github.com/coral-streaming/sbt-coveralls")
      project.dependsOn(RootProject(coverallsPlugin))
    case _ =>
      project
  }

}

