import Dependencies._
import sbt.Keys._
import sbt.{Def, _}

object Settings {

  // for org.broadinstitute.dsde.workbench modules
  val artifactory = "https://broadinstitute.jfrog.io/broadinstitute/"
  val commonResolvers = List(
    "artifactory-releases" at artifactory + "libs-release",
    "artifactory-snapshots" at artifactory + "libs-snapshot"
  )

  //coreDefaultSettings + defaultConfigs = the now deprecated defaultSettings
  val commonBuildSettings: Seq[Def.Setting[_]] = Defaults.coreDefaultSettings ++ Defaults.defaultConfigs ++ Seq(
    // javaOptions += "-Xmx2G",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
  )

  val commonCompilerSettings = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding", "utf8",
    "-target:jvm-1.8",
    "-Xmax-classfile-name", "100",
    "-Xfatal-warnings"
  )

  // test parameters explanation:
  // `-o` - causes test results to be written to the standard output
  //     `F` - Display full stack traces
  //     `D` - Display test duration after test name
  //     (removed on April 22, 2018) `G` - show reminder of failed and canceled tests with full stack traces at the end of log file
  // `-fW` - causes test results to be written to the summary.log without colored text
  val testSettings = List(
    testOptions in Test += Tests.Argument("-oFD", "-u", "test-reports", "-fW", "test-reports/TEST-summary.log")
  )

  //common settings for all sbt subprojects
  val commonSettings: Seq[Def.Setting[_]] =
    commonBuildSettings ++ testSettings ++ List(
    organization  := "org.broadinstitute.dsde.firecloud",
    scalaVersion  := "2.12.6",
    resolvers ++= commonResolvers,
    scalacOptions ++= commonCompilerSettings
  )

  //the full list of settings for the root project that's ultimately the one we build into a fat JAR and run
  //coreDefaultSettings (inside commonSettings) sets the project name, which we want to override, so ordering is important.
  //thus commonSettings needs to be added first.
  val rootSettings: Seq[Def.Setting[_]] = commonSettings ++ List(
    name := "FireCloud-UITests",
    libraryDependencies ++= rootDependencies
  )
}
