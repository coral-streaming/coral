import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import sbt.Keys._
import sbt._

import com.typesafe.sbt.SbtNativePackager.autoImport._
import spray.revolver.RevolverPlugin._

object Packaging {
  import com.typesafe.sbt.SbtNativePackager._

  val packagingSettings = Seq(
    name := Settings.appName,
    NativePackagerKeys.packageName := "coral"
  ) ++ buildSettings

}

object Publishing {
  val nexus = if (Settings.buildVersion.trim.endsWith("SNAPSHOT"))
    sys.props.get("publish.repository.snapshots").map("snapshots" at _)
  else
    sys.props.get("publish.repository.releases").map("releases" at _)
  val publishCredentials = sys.props.get("publish.repository.credentials").map(filename => Credentials(new File(filename))).map(publishCredentials => Seq(credentials += publishCredentials))

  val publishingSettings = Seq(publishTo := nexus) ++ publishCredentials.getOrElse(Seq())
}

object Plugins {
  val enablePlugins = Seq(JavaAppPackaging)
}

object TopLevelBuild extends Build {

  val projectSettings =
    Settings.buildSettings      ++
    Packaging.packagingSettings ++
    Publishing.publishingSettings ++
    Revolver.settings           ++
    Seq (
      resolvers           ++= Resolvers.allResolvers,
      libraryDependencies ++= Dependencies.allDependencies
    )

  lazy val coral = Project (
    id = Settings.appName,
    base = file ("."),
    settings = Defaults.coreDefaultSettings ++ Seq(
      publishLocal := {},
      publish := {}
    )
  ).aggregate(coralCore, coralApi)

  lazy val coralApi = Project (
    id = "runtime-api",
    base = file ("runtime-api"),
    settings = projectSettings
  ).configs( IntegrationTest ).settings( Defaults.itSettings : _*).enablePlugins(Plugins.enablePlugins: _*)

  lazy val coralCore = Project (
    id = "coral-core",
    base = file ("coral-core"),
    settings = projectSettings
  ).configs( IntegrationTest ).settings( Defaults.itSettings : _*).enablePlugins(Plugins.enablePlugins: _*)
}

