package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Reporter
import org.scalatest.events._

class ScalaTestReporter(val aggregateReporter: Reporter) extends Reporter with LazyLogging {

  override def apply(event: Event): Unit = {
    aggregateReporter.apply(event)
    event match {
      case e: TestFailed => logger.error(s"FAILED test: ${e.suiteName}.${e.testName} Because: ${e.message}")
      case e: TestStarting => logger.info(s"Starting: ${e.suiteName}.${e.testName}")
      case e: TestSucceeded => logger.info("SUCCEEDED test: ")
      case _ =>
    }
  }

}
