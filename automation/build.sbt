import Settings._
import sbt._
import sbt.Keys.{fork, javaOptions, parallelExecution, testForkedParallel}

import scala.collection.JavaConverters._


lazy val firecloudUiTests = project.in(file("."))
  .settings(rootSettings:_*)
 //   .settings(inConfig(Test)(Defaults.testSettings),
 //     inThisBuild(List(
        version := "1.0"
 //     )),

  // clean the Console at the the start of each run
  triggeredMessage in ThisBuild := Watched.clearWhenTriggered

  // Can use CTRL-C without exiting SBT
  cancelable in Global := true

// When fork, use the base directory as the working directory
Test / baseDirectory := (baseDirectory in ThisBuild).value

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
Test / fork := true

/**
  * forked tests can optionally be run in parallel.
  */
Test / testForkedParallel := true

/*
 * Enables (true) or disables (false) parallel execution of tasks.
 * In not-forked mode: test classes are run in parallel in different threads, in same sbt jvm.
 * In forked mode: each test class runs tests in sequential order, in a separated jvm.
 */
Test / parallelExecution := true

/**
  * disable sbt's log buffering
  */
logBuffered in Test := false

/**
  * Control the number of forked JVMs allowed to run at the same time by
  *  setting the limit on Tags.ForkedTestGroup tag, which is 1 by default.
  */
// concurrentRestrictions in Global := Seq(Tags.limit(Tags.ForkedTestGroup, 5))
Global / concurrentRestrictions := Seq(Tags.limit(Tags.Test, 5))

/**
  * Forked JVM options
  */
Test / javaOptions ++= Seq("-Xmx6G")
// javaOptions in Test ++= Seq("-Xms1G", "-Xmx2G")

// Test / javaOptions ++= Seq(s"-Dlogback.configurationFile=${(Test / baseDirectory).value}/logback-test.xml")

// Test / javaOptions ++= Seq(s"-Djava.util.logging.config.file=${(Test / baseDirectory).value}/logback-test.xml")

// Test / javaOptions ++= Seq(s"-Dheadless=${Option(System.getProperty("headless")).getOrElse("false")}")

// Test / javaOptions ++= Seq(s"-Djsse.enableSNIExtension=${Option(System.getProperty("jsse.enableSNIExtension")).getOrElse("false")}")

// copy over all system properties
Test / javaOptions ++= Seq({
  val props = System.getProperties
  props.stringPropertyNames().asScala.toList.map { key => s"-D$key=${props.getProperty(key)}"}.mkString(" ")
})



/*
testGrouping in Test := (definedTests in Test).value.map { test =>
  new Tests.Group(
    name = test.name,
    tests = Seq(test),
    runPolicy = Tests.SubProcess(
      ForkOptions(
        javaHome = javaHome.value,
        connectInput = true,
        //outputStrategy = Some(StdoutOutput), // outputStrategy.value,
        runJVMOptions = Some((javaOptions in Test).value ++ Seq(s"-Dtest.name=${test.name}", s"-Ddir.name=${baseDirectory.value}")),
        workingDirectory = Some((Test / baseDirectory).value),
        envVars = Map("test.name" -> test.name)
      )
    )
  )
}

*/


testGrouping in Test := (definedTests in Test).value.map { test =>
  println("test.name: " + test.name)
  println("(Test/baseDirectory).value: " + (Test / baseDirectory).value)
  println("(baseDirectory in ThisBuild).value: " + (baseDirectory in ThisBuild).value)

  val envirn = System.getenv()
  envirn.keySet.forEach {
    key => s"-D$key=${envirn.get(key)}"
      println(s"-D$key=${envirn.get(key)}")
  }
  val options = ForkOptions()
    .withConnectInput(true)
    .withWorkingDirectory(Some((Test / baseDirectory).value))
    .withOutputStrategy(Some(sbt.StdoutOutput))
    .withRunJVMOptions(
      Vector(
      s"-Dlogback.configurationFile=${(Test / baseDirectory).value.getAbsolutePath}/logback-test.xml",
      s"-Djava.util.logging.config.file=${(Test / baseDirectory).value.getAbsolutePath}/logback-test.xml",
      s"-Dtest.name=${test.name}",
      s"-Ddir.name=${(Test / baseDirectory).value}",
      s"-Dheadless=${Option(System.getProperty("headless")).getOrElse("false")}",
      s"-Djsse.enableSNIExtension=${Option(System.getProperty("jsse.enableSNIExtension")).getOrElse("false")}"))
  new Tests.Group(
    name = test.name,
    tests = Seq(test),
    runPolicy = Tests.SubProcess(options)
  )
}


