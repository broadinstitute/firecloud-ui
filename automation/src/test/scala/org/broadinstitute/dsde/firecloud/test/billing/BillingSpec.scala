package org.broadinstitute.dsde.firecloud.test.billing

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Rawls
import org.broadinstitute.dsde.firecloud.config.{AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.{MethodData, SimpleMethodConfig, TestData}
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec, Tags}
import org.scalatest.{FreeSpec, Ignore, Matchers}

/**
  * Tests related to billing accounts.
  */
@Ignore
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
        withClue(s"Creating billing project: $billingProjectName") { status shouldEqual "success" }
      }

      "with a new billing project" - {

        "should be able to add a user to the billing project" in withWebDriver { implicit driver =>
          implicit val authToken = AuthTokens.hermione
          val secondUser = Config.Users.harry.email

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-add-user-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthTokens.dumbledore)
          api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)

          signIn(Config.Users.owner)
          val billingPage = new BillingManagementPage().open
          billingPage.openBillingProject(billingProjectName)
          billingPage.addUserToBillingProject(secondUser, "User")

          val isSuccess = billingPage.isUserInBillingProject(secondUser)
          isSuccess shouldEqual true
        }

        "should be able to create a workspace in the billing project" in withWebDriver { implicit driver =>
          // Create new billing project
          implicit val authToken = AuthTokens.hermione

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-make-ws-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthTokens.dumbledore)
          api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)

          // create workspace and verify
          signIn(Config.Users.hermione)
          val workspaceName = "BillingSpec_makeWorkspace_" + randomUuid
          val listPage = new WorkspaceListPage().open
          register cleanUp api.workspaces.delete(billingProjectName, workspaceName)
          val detailPage = listPage.createWorkspace(billingProjectName, workspaceName)

          detailPage.validateWorkspace shouldEqual true
        }

        "should be able to run a method in a new workspace in the billing project" in withWebDriver { implicit driver =>
          // Create new billing project
          implicit val authToken = AuthTokens.hermione

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-method-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthTokens.dumbledore)
          api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)

          // create workspace
          val workspaceName = "BillingSpec_runMethod_" + randomUuid
          register cleanUp api.workspaces.delete(billingProjectName, workspaceName)
          api.workspaces.create(billingProjectName, workspaceName)

          api.importMetaData(billingProjectName, workspaceName, "entities", TestData.SingleParticipant.participantEntity)

          // verify running a method
          signIn(Config.Users.hermione)
          val methodConfigName: String = "test_method" + UUID.randomUUID().toString
          val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProjectName, workspaceName).open
          val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, methodConfigName)
          methodConfigDetailsPage.editMethodConfig(inputs = Some(SimpleMethodConfig.inputs))
          val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, TestData.SingleParticipant.entityId)

          submissionDetailsPage.waitUntilSubmissionCompletes()
          assert(submissionDetailsPage.verifyWorkflowSucceeded())
        }
      }
    }
  }
}
