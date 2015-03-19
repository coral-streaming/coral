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

  lazy val coral = Project (
    id = Settings.appName,
    base = file (".")
  ).aggregate(
      runtimeApi
  )

  lazy val runtimeApi = Project (
    id = "runtime-api",
    base = file ("runtime-api"),
    settings = Settings.buildSettings ++
      Packaging.packagingSettings ++
      Revolver.settings ++
      Seq (
        resolvers ++= Resolvers.allResolvers,
        libraryDependencies ++= Dependencies.allDependencies
      )
  )
}

