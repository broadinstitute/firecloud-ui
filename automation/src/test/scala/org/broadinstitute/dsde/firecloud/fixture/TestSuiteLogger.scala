package org.broadinstitute.dsde.firecloud.fixture

import org.scalatest.{Args, Status, Suite}


trait TestSuiteLogger { self: Suite =>

  override protected def runTest(testName: String, args: Args): Status = {
    val testReporter = new ScalaTestReporter(args.reporter)
    self.runTest(testName, args.copy(reporter = testReporter))
  }
}
