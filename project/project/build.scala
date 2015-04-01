import sbt._

object PluginDef extends Build {
  override lazy val projects = Seq(root)
  lazy val root = Project("plugins", file(".")).dependsOn( coverallsPlugin )
  lazy val coverallsPlugin = uri("git://github.com/coral-streaming/sbt-coveralls")
}

