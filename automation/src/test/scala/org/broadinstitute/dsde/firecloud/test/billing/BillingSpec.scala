package org.broadinstitute.dsde.firecloud.test.billing

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Rawls
import org.broadinstitute.dsde.firecloud.config.{AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.TestData
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
import org.broadinstitute.dsde.firecloud.page.workspaces.{WorkspaceListPage, WorkspaceMethodConfigPage}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
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
        val billingProjectName = "billing-spec-create-" + makeRandomId() // is this a unique ID?
        logger.info(s"Creating billing project: $billingProjectName")

        billingPage.createBillingProject(billingProjectName, Config.Projects.billingAccount)
        register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthTokens.dumbledore)

        val status = billingPage.waitForCreateCompleted(billingProjectName)
        status shouldEqual "success"
      }

      "with a new billing project" - {

        "should be able to add a user to the billing project" in withWebDriver { implicit driver =>
          implicit val authToken = AuthTokens.owner
          val secondUser = Config.Users.testUser.email
          signIn(Config.Users.owner)

          val billingPage = new BillingManagementPage().open
          val billingProjectName = "billing-spec-create-" + makeRandomId() // is this a unique ID?
          logger.info(s"Creating billing project: $billingProjectName")

          billingPage.createBillingProject(billingProjectName, Config.Projects.billingAccount)
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthTokens.dumbledore)

          billingPage.waitForCreateCompleted(billingProjectName)

          billingPage.openBillingProject(billingProjectName)
          billingPage.addUserToBillingProject(secondUser, "User")

          val isSuccess = billingPage.isUserInBillingProject(secondUser)
          isSuccess shouldEqual true
        }

        "should be able to create a workspace in the billing project" in withWebDriver { implicit driver =>
          // Create new billing project
          implicit val authToken = AuthTokens.owner
          signIn(Config.Users.owner)

          val billingPage = new BillingManagementPage().open
          val billingProjectName = "billing-spec-create-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")

          billingPage.createBillingProject(billingProjectName, Config.Projects.billingAccount)
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthTokens.dumbledore)

          billingPage.waitForCreateCompleted(billingProjectName)

          // create workspace and verify
          val workspaceName = "WorkspaceSpec_create_" + randomUuid
          val listPage = new WorkspaceListPage().open
          val detailPage = listPage.createWorkspace(billingProjectName, workspaceName)
          register cleanUp api.workspaces.delete(billingProjectName, workspaceName)

          detailPage.awaitLoaded()
          detailPage.ui.readWorkspaceName shouldEqual workspaceName
        }

        "should be able to run a method in a new workspace in the billing project" in withWebDriver { implicit driver =>
          // Create new billing project
          implicit val authToken = AuthTokens.owner
          signIn(Config.Users.owner)

          val billingPage = new BillingManagementPage().open
          val billingProjectName = "billing-spec-create-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")

          billingPage.createBillingProject(billingProjectName, Config.Projects.billingAccount)
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthTokens.dumbledore)

          val status = billingPage.waitForCreateCompleted(billingProjectName)

          // create workspace
          val workspaceName = "WorkspaceSpec_create_" + randomUuid
          val listPage = new WorkspaceListPage().open
          val detailPage = listPage.createWorkspace(billingProjectName, workspaceName)
          register cleanUp api.workspaces.delete(billingProjectName, workspaceName)

          detailPage.awaitLoaded()

          // verify running a method
          api.importMetaData(billingProjectName, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
          val methodConfigName: String = "test_method" + UUID.randomUUID().toString


          val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(billingProjectName, workspaceName).open
          val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(TestData.SimpleMethod.namespace,
            TestData.SimpleMethod.name, TestData.SimpleMethod.snapshotId, methodConfigName)
          methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethod.inputs))
          val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(TestData.SimpleMethod.rootEntityType, TestData.SingleParticipant.entityId)

          submissionDetailsPage.waitUntilSubmissionCompletes()
          assert(submissionDetailsPage.verifyWorkflowSucceeded())
        }

      }
    }
  }
}
