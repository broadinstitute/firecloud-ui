package org.broadinstitute.dsde.firecloud.test.billing

import java.util.UUID

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.model.{UserInfo, WorkbenchEmail, WorkbenchUserId}
import org.broadinstitute.dsde.workbench.service.{Google, Rawls}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

/**
  * Tests related to billing accounts.
  */
class BillingSpec extends FreeSpec with WebBrowserSpec with UserFixtures with CleanUp
  with Matchers with WorkspaceFixtures with BillingFixtures with LazyLogging {

  "A user" - {
    "with a billing account" - {
      "with a new billing project" - {
        /*
         * This test does a lot more than we would traditionally like, but it's a sacrifice we will
         * make because creating billing projects is slow and error-prone (because of Google).
         *
         * This single test was originally five test cases:
         * A) Create a new billing project (in the UI)
         * B) Create a new billing project + add a new user to that project
         * C) Create a new billing project + create a new workspace in that project
         * D) Create a new billing project + create a new workspace in that project + run an analysis in that workspace
         * E) Should be able to change the billing account associated with the project
         *
         * A was a subset of B, C and D
         * C was a subset of D
         *
         * Clearly a lot of functionality bleeds between these test cases. In order to save runtime during
         * the tests, we're combining all five. The idea is that if any piece of basic functionality
         * in these tests was broken (creating a workspace in a new project, creating a new project, etc), then
         * most of these tests would fail anyway so grouping them together isn't much worse.
         *
         * NOTE: this single test case MUST create a brand new billing project each time it's run. If we use a
         * project from GPAlloc, this entire test becomes worthless and our test coverage for billing is garbage.
         *
         * E comes last because it makes the project unusable.
         */
        "should be able add a new user, create a workspace, and run a method, change billing account" in {
          val user = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = user.makeAuthToken()
          val secondUser = UserPool.chooseStudent.email
          val testData = TestData()

          /*
           * This must continue to create a new billing project rather than using an allocated one.
           * Otherwise it's not covering the entirety of the intended scenario (which is one that we
           * have seen break in the past that relied on using a brand new billing project).
           */
          withWebDriver { implicit driver =>
            withSignIn(user) { listPage =>
              //BEGIN: Test creating billing project in UI
              val billingPage = new BillingManagementPage().open
              val billingProjectName = createNewBillingProject(user, billingPage)
              //END: Test creating billing project in UI

              //BEGIN: Test creating workspace
              val workspaceName = "BillingSpec_makeWorkspace_" + randomUuid
              register cleanUp api.workspaces.delete(billingProjectName, workspaceName)
              val detailPage = listPage.open.createWorkspace(billingProjectName, workspaceName)
              //END: Test creating workspace

              //BEGIN: Test running analysis in workspace
              api.workspaces.waitForBucketReadAccess(billingProjectName, workspaceName)
              api.importMetaData(billingProjectName, workspaceName, "entities", testData.participantEntity)

              val methodConfigName: String = "test_method" + UUID.randomUUID().toString
              api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProjectName, workspaceName, SimpleMethodConfig.configNamespace,
                SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

              // verify running a method
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProjectName, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
              val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, testData.participantId)

              submissionDetailsPage.waitUntilSubmissionCompletes()
              assert(submissionDetailsPage.verifyWorkflowSucceeded())
              //END: Test running anlysis in workspace

              //BEGIN: Test adding user to project
              billingPage.open.openBillingProject(billingProjectName)
              billingPage.addUserToBillingProject(secondUser, "User")

              val isSuccess = billingPage.isUserInBillingProject(secondUser)
              isSuccess shouldEqual true
              //END: Test adding user to project

              //BEGIN: should be able to change the billing account associated with the project
              val originalBillingAccount = Google.billing.getBillingProjectAccount(billingProjectName)
              originalBillingAccount shouldBe Some(Config.Projects.billingAccountId)

              Google.billing.removeBillingProjectAccount(billingProjectName)

              val newBillingAccount = Google.billing.getBillingProjectAccount(billingProjectName)
              newBillingAccount shouldBe None
              //END: should be able to change the billing account associated with the project
            }
          }
        }
      }
    }
  }

  private def createNewBillingProject(user: Credentials, billingPage: BillingManagementPage, trials: Int = 3): String = {
    val billingProjectName = "billing-spec-create-" + makeRandomId()
    logger.info(s"Creating billing project: $billingProjectName")

    billingPage.createBillingProject(billingProjectName, Config.Projects.billingAccount)
    register cleanUp Rawls.admin.deleteBillingProject(billingProjectName, UserInfo(OAuth2BearerToken(user.makeAuthToken().value), WorkbenchUserId("0"), WorkbenchEmail(user.email), 3600))(UserPool.chooseAdmin.makeAuthToken())

    val statusOption = billingPage.waitForCreateDone(billingProjectName)

    statusOption match {
      case None | Some("failure") if trials > 1 =>
        logger.info(s"failure or timeout creating project $billingProjectName, retrying ${trials-1} more times")
        createNewBillingProject(user, billingPage, trials-1)
      case None =>
        fail(s"timed out waiting billing project $billingProjectName to be ready")
      case Some(status) =>
        withClue(s"Creating billing project: $billingProjectName") {
          status shouldEqual "success"
        }
        billingProjectName
    }
  }
}
