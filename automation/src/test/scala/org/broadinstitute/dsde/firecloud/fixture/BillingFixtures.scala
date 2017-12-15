package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.api.{Orchestration, Rawls}
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.config.{Config, UserPool}
import org.broadinstitute.dsde.firecloud.test.CleanUp
import org.scalatest.TestSuite

import scala.util.Random

trait BillingFixtures extends CleanUp { self: TestSuite =>

  // copied from WebBrowserSpec so we don't have to self-type it
  // TODO make it common to API and browser tests
  private def makeRandomId(length: Int = 7): String = {
    Random.alphanumeric.take(length).mkString.toLowerCase
  }

  def withBillingProject(namePrefix: String)
                        (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val billingProjectName = namePrefix + "-" + makeRandomId()
    Orchestration.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)
    try {
      testCode(billingProjectName)
    } finally {
      try {
        Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
      } catch nonFatalAndLog(s"Error deleting billing project in withBillingProject clean-up: $billingProjectName")
    }
  }
}
