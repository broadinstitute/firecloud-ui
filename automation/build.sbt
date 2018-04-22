import Settings._
import sbt._
import sbt.Keys.{fork, javaOptions, parallelExecution, testForkedParallel}
import scala.collection.JavaConversions._


lazy val firecloudUiTests = project.in(file("."))
  .settings(rootSettings:_*)

version := "1.0"


/**
  * Specify that all tests will be executed in a single external JVM.
  * By default, tests executed in a forked JVM are executed sequentially.
  */
fork in Test := true

/**
  * forked tests can optionally be run in parallel. This feature is still considered experimental.
  */
testForkedParallel in Test := true

/*
 * Enables (true) or disables (false) parallel execution of tasks
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
concurrentRestrictions in Global := Seq(
  Tags.limit(Tags.ForkedTestGroup, 6),
  Tags.limit(Tags.Test, 6)
)

javaOptions := Seq(s"-Dlogback.configurationFile=${Option(System.getProperty("user.dir")).get}/logback-test.xml")

javaOptions in Test ++= Seq(s"-Djava.util.logging.config.file=${Option(System.getProperty("user.dir")).get}/logback-test.xml")
javaOptions in Test ++= Seq("-Xms512M", "-Xmx2G", "-Djsse.enableSNIExtension=false", "-Dheadless=false")
javaOptions in Test ++= Seq(s"-Dheadless=${Option(System.getProperty("headless")).getOrElse("false")}")
javaOptions in Test ++= Seq(s"-Djsse.enableSNIExtension=${Option(System.getProperty("jsse.enableSNIExtension")).getOrElse("false")}")

javaOptions in Test ++= Seq({
  val props = System.getProperties
  props.stringPropertyNames().toList.map { key => s"-D$key=${props.getProperty(key)}"}.mkString(" ")
})


testGrouping in Test := (definedTests in Test).value.map { test =>
  println("test.name: " + test.name)
  println("javaOptions in Test: " + (javaOptions in Test).value.mkString)
  println("outputStrategy.value: " + Some(StdoutOutput).getOrElse(""))
  println("javaHome.value: " + javaHome.value)
  println("envVars.value: " + envVars.value.toString())
  println("************")
  println("************")

  Tests.Group(
    name = test.name,
    tests = Seq(test),
    runPolicy = Tests.SubProcess(
      ForkOptions(
        bootJars = Nil,
        javaHome = javaHome.value,
        connectInput = connectInput.value,
        outputStrategy = Some(StdoutOutput), // outputStrategy.value,
        runJVMOptions = (javaOptions in Test).value ++ Seq("-Dtest.name=" + test.name),
        workingDirectory = Some(baseDirectory.value),
        envVars = envVars.value ++ Map("test.name" -> test.name)
      )
    )
  )
}
