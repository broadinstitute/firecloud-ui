package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Reporter
import org.scalatest.events.{Event, TestFailed, TestStarting, TestSucceeded}

case class TestEventReporter(aggregateReporter: Reporter) extends Reporter with LazyLogging {
  override def apply(event: Event): Unit = {
    aggregateReporter.apply(event) // calling super apply(event)
    event match {
      case evt: TestFailed => logger.error(s"Test Failed:: " + evt.suiteName + " -- " + evt.testName)
      case evt: TestStarting => logger.info("Test Starting:: " + evt.suiteName + " -- " + evt.testName)
      case evt: TestSucceeded => logger.info("Test Succeeded:: " + evt.suiteName + " -- " + evt.testName)
      case evt => // ignore other events
    }
  }
}
