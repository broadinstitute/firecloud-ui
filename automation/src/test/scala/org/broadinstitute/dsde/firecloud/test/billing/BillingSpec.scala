package org.broadinstitute.dsde.firecloud.test.billing

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Rawls
import org.broadinstitute.dsde.firecloud.auth.{AuthToken, UserAuthToken}
import org.broadinstitute.dsde.firecloud.config.{Config, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.{MethodData, SimpleMethodConfig, TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.{WorkspaceMethodConfigDetailsPage, WorkspaceMethodConfigListPage}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Ignore, Matchers}

/**
  * Tests related to billing accounts.
  */
class BillingSpec extends FreeSpec with WebBrowserSpec with UserFixtures with CleanUp
  with Matchers with LazyLogging {

  "A user" - {
    "with a billing account" - {
      // Need to tweak background sign-in to make this test work
      "should be able to create a billing project" ignore withWebDriver { implicit driver =>
        val userOwner = UserPool.chooseProjectOwner
        implicit val authToken: AuthToken = userOwner.makeAuthToken()
        withSignIn(userOwner) { _ =>
          val billingPage = new BillingManagementPage().open
          val billingProjectName = "billing-spec-create-" + makeRandomId() // is this a unique ID?
          logger.info(s"Creating billing project: $billingProjectName")

          billingPage.createBillingProject(billingProjectName, Config.Projects.billingAccount)
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())

          val status = billingPage.waitForCreateCompleted(billingProjectName)
          withClue(s"Creating billing project: $billingProjectName") { status shouldEqual "success" }
        }
      }

      "with a new billing project" - {

        "should be able to add a user to the billing project" in withWebDriver { implicit driver =>
          val ownerUser = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = ownerUser.makeAuthToken()
          val secondUser = UserPool.chooseStudent.email

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-add-user-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
          api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)

          withSignIn(ownerUser) { _ =>
            val billingPage = new BillingManagementPage().open
            billingPage.openBillingProject(billingProjectName)
            billingPage.addUserToBillingProject(secondUser, "User")

            val isSuccess = billingPage.isUserInBillingProject(secondUser)
            isSuccess shouldEqual true
          }

        }

        "should be able to create a workspace in the billing project" in withWebDriver { implicit driver =>
          // Create new billing project
          val user = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = user.makeAuthToken()

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-make-ws-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
          api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)

          // create workspace and verify
          withSignIn(user) { listPage =>
            val workspaceName = "BillingSpec_makeWorkspace_" + randomUuid
            register cleanUp api.workspaces.delete(billingProjectName, workspaceName)
            val detailPage = listPage.createWorkspace(billingProjectName, workspaceName)
          }

        }

        "should be able to run a method in a new workspace in the billing project" in withWebDriver { implicit driver =>
          // Create new billing project
          val user = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = user.makeAuthToken()

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-method-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(UserPool.chooseAdmin.makeAuthToken())
          api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)

          // create workspace
          val workspaceName = "BillingSpec_runMethod_" + randomUuid
          register cleanUp api.workspaces.delete(billingProjectName, workspaceName)
          api.workspaces.create(billingProjectName, workspaceName)

          api.importMetaData(billingProjectName, workspaceName, "entities", TestData.SingleParticipant.participantEntity)

          val methodConfigName: String = "test_method" + UUID.randomUUID().toString
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProjectName, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

          // verify running a method
          withSignIn(user) { _ =>
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProjectName, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, TestData.SingleParticipant.entityId)

            submissionDetailsPage.waitUntilSubmissionCompletes()
            assert(submissionDetailsPage.verifyWorkflowSucceeded())
          }
        }
      }
    }
  }
}
