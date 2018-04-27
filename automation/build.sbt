import Settings._
import sbt._
import sbt.Keys.{fork, javaOptions, parallelExecution, testForkedParallel}
import scala.collection.JavaConversions._


lazy val firecloudUiTests = project.in(file("."))
  .settings(rootSettings:_*)

version := "1.0"

/**
  * sbt forking jvm -- sbt provides 2 testing modes: forked vs not forked.
  * -- forked: each task (test class) is executed sequentially in a forked JVM.
  *    Test results are segregated, easy to read.
  * -- not forked: all tasks (test classes) are executed in same sbt JVM.
  *    Test results are not segregated, hard to read.
  *
  */

/**
  * Specify that all tests will be executed in a single external JVM.
  * By default, tests executed in a forked JVM are executed sequentially.
  */
fork in Test := true

/**
  * forked tests can optionally be run in parallel.
  */
testForkedParallel in Test := true

/*
 * Enables (true) or disables (false) parallel execution of tasks.
 * In not-forked mode: test classes are run in parallel in different threads, in same sbt jvm.
 * In forked mode: each test class runs tests in sequential order, in a separated jvm.
 */
parallelExecution in Test := true

/**
  * disable sbt's log buffering
  */
logBuffered in Test := false

/**
  * Control the number of forked JVMs allowed to run at the same time by
  *  setting the limit on Tags.ForkedTestGroup tag, which is 1 by default.
  */
concurrentRestrictions in Global := Seq(Tags.limit(Tags.ForkedTestGroup, 5)) //, Tags.limit(Tags.Test, 5))

javaOptions in Test ++= Seq(s"-Dlogback.configurationFile=${baseDirectory.value}/logback-test.xml")
javaOptions in Test ++= Seq(s"-Djava.util.logging.config.file=${baseDirectory.value}/logback-test.xml")
javaOptions in Test ++= Seq("-Xms1G", "-Xmx2G")
javaOptions in Test ++= Seq(s"-Dheadless=${Option(System.getProperty("headless")).getOrElse("false")}")
javaOptions in Test ++= Seq(s"-Djsse.enableSNIExtension=${Option(System.getProperty("jsse.enableSNIExtension")).getOrElse("false")}")

javaOptions in Test ++= Seq({
  val props = System.getProperties
  props.stringPropertyNames().toList.map { key => s"-D$key=${props.getProperty(key)}"}.mkString(" ")
})


testGrouping in Test := (definedTests in Test).value.map { test =>
  new Tests.Group(
    name = test.name,
    tests = Seq(test),
    runPolicy = Tests.SubProcess(
      ForkOptions(
        bootJars = Nil,
        javaHome = javaHome.value,
        connectInput = connectInput.value,
        outputStrategy = Some(StdoutOutput), // outputStrategy.value,
        runJVMOptions = (javaOptions in Test).value ++ Seq(s"-Dtest.name=${test.name}", s"-Ddir.name=${baseDirectory.value}"),
        workingDirectory = Some(baseDirectory.value),
        envVars = envVars.value ++ Map("test.name" -> test.name)
      )
    )
  )
}
