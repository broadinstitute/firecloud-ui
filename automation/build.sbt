import Settings._
import sbt.Keys.{fork, javaOptions, parallelExecution, testForkedParallel}

import scala.collection.JavaConverters._


lazy val firecloudUiTests = project.in(file("."))
  .settings(rootSettings:_*)

version := "1.0"

// clean the Console at the the start of each run
triggeredMessage in ThisBuild := Watched.clearWhenTriggered

// Can use CTRL-C without exiting SBT
cancelable in Global := true


/**
  * sbt forking JVM -- sbt provides 2 testing modes: forked vs not forked.
  * -- forked: each task (test class) runs in a new forked JVM.
  *    Test results are segregated, easy to read.
  * -- not forked: all tasks (test classes) are executed in same sbt JVM.
  *    Test results are not segregated, hard to read.
  *
  */

/**
  * Specify that each test class will be mapped to run in its own JVM.
  */
Test / fork := true

/**
  * Forked test classes can run in parallel
  */
Test / testForkedParallel := true

/**
  * When fork, use the base directory as the working directory
  */
Test / baseDirectory := (baseDirectory in ThisBuild).value

/*
 * Each test class is mapped to its own task.
 * Enables (true) or disables (false) parallel execution of tasks.
 */
Test / parallelExecution := true

/**
  * disable sbt's log buffering
  */
Test / logBuffered := false

/**
  * Control the number of forked JVM allowed to run at the same time by
  *  setting the limit on Tags.ForkedTestGroup tag, which is 1 by default.
  *
  *  Warning: can't set too high (set at 10 would crashes OS)
  *  This is not number of threads in each JVM. That would be up to sbt.
  */
Global / concurrentRestrictions := Seq(Tags.limit(Tags.ForkedTestGroup, 6))

/**
  * Forked JVM options
  */
Test / javaOptions ++= Seq("-Xmx4G")

/**
 * copy system properties to forked JVM
  */
Test / javaOptions ++= propertiesAsScalaMap(System.getProperties).map{ case (key,value) => "-D" + key + "=" +value }.toSeq

// only show stack traces up to the first sbt stack frame
traceLevel := 0

/*
 * This works only in SBT version pre 1.x release. Save this until we're certain we don't need it.
 */
/*  testGrouping in Test := (definedTests in Test).value.map { test =>
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
}  */



testGrouping in Test := {
  (definedTests in Test).value.map { test =>

    /**
      * debugging print out:
      *
      * println("test.name: " + test.name)
      * println("(Test/baseDirectory).value: " + (Test / baseDirectory).value)
      * println("(baseDirectory in ThisBuild).value: " + (baseDirectory in ThisBuild).value)
      *
      * val envirn = System.getenv()
      *   envirn.keySet.forEach {
      * key => s"-D$key=${envirn.get(key)}"
      * println(s"-D$key=${envirn.get(key)}")
      * }
      */

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
    Tests.Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = Tests.SubProcess(options)
    )
  }
}
