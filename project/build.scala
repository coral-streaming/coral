// Natalino Busa
// http://www.linkedin.com/in/natalinobusa

import sbt.Keys._
import sbt._

import com.typesafe.sbt.SbtNativePackager.autoImport._
import spray.revolver.RevolverPlugin._

object Packaging {
  import com.typesafe.sbt.SbtNativePackager._

  val packagingSettings = Seq(
    name := Settings.appName,
    NativePackagerKeys.packageName := "natalinobusa"
  ) ++ Seq(packageArchetype.java_application:_*) ++ buildSettings

}

object TopLevelBuild extends Build {

  val projectSettings =
    Settings.buildSettings      ++
    Packaging.packagingSettings ++
    Revolver.settings           ++
    Seq (
      resolvers           ++= Resolvers.allResolvers,
      libraryDependencies ++= Dependencies.allDependencies
    )

  lazy val coral = Project (
    id = Settings.appName,
    base = file (".")
  ).aggregate(coralCore, coralApi)

  lazy val coralApi = Project (
    id = "runtime-api",
    base = file ("runtime-api"),
    settings = projectSettings
  ).configs( IntegrationTest ).settings( Defaults.itSettings : _*)

  lazy val coralCore = Project (
    id = "coral-core",
    base = file ("coral-core"),
    settings = projectSettings
  ).configs( IntegrationTest ).settings( Defaults.itSettings : _*)
}

