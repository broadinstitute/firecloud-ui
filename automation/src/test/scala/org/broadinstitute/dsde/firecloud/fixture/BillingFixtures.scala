package org.broadinstitute.dsde.firecloud.fixture

import org.broadinstitute.dsde.firecloud.api.Rawls
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.config.{Config, UserPool}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.Suite

trait BillingFixtures extends CleanUp { self: WebBrowserSpec with Suite =>

  def withBillingProject[T](billingProjectPrefix: String)(f: String => T)(implicit authToken: AuthToken): T = {
    val billingProjectName = s"$billingProjectPrefix-${makeRandomId()}"
    logger.info(s"Creating billing project: $billingProjectName")
    register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
    api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)

    f(billingProjectName)
  }

}
