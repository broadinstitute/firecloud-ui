package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.api.Rawls
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.config.{Config, UserPool}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.Suite

trait BillingFixtures extends CleanUp { self: WebBrowserSpec with Suite =>
  def withBillingProject(namePrefix: String)
                        (testCode: (String) => Any)(implicit token: AuthToken): Unit = {
    val billingProjectName = namePrefix + "-" + makeRandomId()
    api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)
    try {
      testCode(billingProjectName)
    } finally {
      try {
        Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
      } catch nonFatalAndLog(s"Error deleting billing project in withBillingProject clean-up: $billingProjectName")
    }
  }
}
