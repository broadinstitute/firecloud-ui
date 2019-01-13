import sbt._
import sbt.Keys._

object Dependencies {
  val jacksonV = "2.9.0"

  val serviceTestV = "0.16-664b08a-SNAP"

  val workbenchExclusions = Seq(
    ExclusionRule(organization = "org.broadinstitute.dsde.workbench", name = s"workbench-metrics_2.12")
  )

  val workbenchServiceTest: ModuleID = "org.broadinstitute.dsde.workbench" %% "workbench-service-test" % serviceTestV % "test" classifier "tests" excludeAll(workbenchExclusions:_*)

  val rootDependencies = Seq(
    // proactively pull in latest versions of Jackson libs, instead of relying on the versions
    // specified as transitive dependencies, due to OWASP DependencyCheck warnings for earlier versions.
    "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonV,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonV,
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonV,
    "com.fasterxml.jackson.module" % "jackson-module-scala_2.12" % jacksonV,

    "org.scalatest"       %%  "scalatest"       % "3.0.5"   % "test",
    "org.seleniumhq.selenium" % "selenium-java" % "3.141.59"  % "test",
    "org.slf4j" % "slf4j-api" % "1.7.25" % "test",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",

    workbenchServiceTest,

    // required by workbenchGoogle
    "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.6" % "provided"
  )
}
