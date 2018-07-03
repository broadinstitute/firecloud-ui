package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import org.scalatest.concurrent.Eventually.scaled
import org.scalatest.time.{Seconds, Span}

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

  abstract override def withFixture(test: NoArgTest): Outcome = {
    super.withFixture(test) match {
      case failed: Failed =>
        if (isRetryable(test)) {
          logger.warn(s"About to retry failed test -- " + test.name)
          withRetryOnFailure(scaled(Span(30, Seconds)))(super.withFixture(test))
        } else {
          super.withFixture(test) // don't retry if not tagged with `taggedAs(Retryable)` even if test failed
        }
      case other => other
    }
  }

}
