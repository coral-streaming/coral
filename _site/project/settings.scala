import sbt._
import Keys._

object Settings {
  val appName = "coral"

  val buildOrganization = "com.natalinobusa"
  val buildVersion      = "1.0.0"
  val buildScalaVersion = "2.11.4"

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := buildOrganization,
    version       := buildVersion,
    scalaVersion  := buildScalaVersion,
    shellPrompt   := ShellPrompt.buildShellPrompt,
    scalacOptions := Seq("-deprecation", "-feature", "-encoding", "utf8")
  )
}

// Shell prompt which show the current project, 
// git branch and build version
object ShellPrompt {
  object devnull extends ProcessLogger {
    def info (s: => String) {}
    def error (s: => String) { }
    def buffer[T] (f: => T): T = f
  }
  def currBranch = (
    ("git status -sb" lines_! devnull headOption)
      getOrElse "-" stripPrefix "## "
  )

  val buildShellPrompt = { 
    (state: State) => {
      val currProject = Project.extract (state).currentProject.id
      "%s:%s:%s> ".format (
        currProject, currBranch, Settings.buildVersion
      )
    }
  }
}

