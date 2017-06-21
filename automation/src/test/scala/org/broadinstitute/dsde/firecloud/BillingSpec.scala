package org.broadinstitute.dsde.firecloud

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Rawls
import org.broadinstitute.dsde.firecloud.auth.AuthTokens
import org.broadinstitute.dsde.firecloud.pages.{BillingManagementPage, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

/**
  * Tests related to billing accounts.
  */
class BillingSpec extends FreeSpec with WebBrowserSpec with CleanUp
  with Matchers with LazyLogging {

  "A user" - {
    "with a billing account" - {
      "should be able to create a billing project" in withWebDriver { implicit driver =>
        implicit val authToken = AuthTokens.owner
        signIn(Config.Users.owner)

        val billingPage = new BillingManagementPage().open
        val projectName = "billing-spec-create-" + makeRandomId()
        logger.info(s"Creating billing project: $projectName")

        billingPage.createBillingProject(projectName, Config.Projects.billingAccount)
        register cleanUp Rawls.admin.deleteBillingProject(projectName)(AuthTokens.dumbledore)

        val status = billingPage.waitForCreateCompleted(projectName)
        status shouldEqual "success"
      }

      "should be able to add a user to a billing project" in withWebDriver { implicit driver =>
        // TODO
      }
    }
  }
}
