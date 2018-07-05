package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.LazyLogging
import org.scalatest._

trait FailTestRetryable extends TestSuiteMixin with LazyLogging with Retries { this: TestSuite =>

  abstract override def run(testName: Option[String], args: Args): Status = {

    val rep = TestEventReporter(args.reporter)
    val status = super.run(testName, args.copy(reporter = rep))

    if (status.succeeds()) {
      status
    } else {
      super.run(testName, args)
    }
  }

  val maxRetries = 1

  abstract override def withFixture(test: NoArgTest): Outcome = {
    if (isRetryable(test))
      withRetryFixture(test, maxRetries)
    else
      super.withFixture(test)
  }

  private def withRetryFixture(test: NoArgTest, retryCount: Int): Outcome = {
    val outcome = super.withFixture(test)
    outcome match {
      case Failed(_) =>
        if (retryCount == 1) {
          logger.warn(s"About to retry failed test -- " + test.name)
          super.withFixture(test)
        } else {
          withRetryFixture(test, retryCount - 1)
        }
      case other => other
    }
  }

}
