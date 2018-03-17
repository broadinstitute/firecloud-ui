package org.broadinstitute.dsde.firecloud.test.billing

import java.util.UUID

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
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
      "should be able to create a billing project" in withWebDriver { implicit driver =>
        val userOwner = UserPool.chooseProjectOwner
        implicit val authToken: AuthToken = userOwner.makeAuthToken()

        val billingProjectName = "billing-spec-create-" + makeRandomId() // is this a unique ID?

        withSignIn(userOwner) { _ =>
          val billingPage = new BillingManagementPage().open
          logger.info(s"Creating billing project: $billingProjectName")

          billingPage.createBillingProject(billingProjectName, Config.Projects.billingAccount)
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName, UserInfo(OAuth2BearerToken(userOwner.makeAuthToken().value), WorkbenchUserId("0"), WorkbenchEmail(userOwner.email), 3600))(UserPool.chooseAdmin.makeAuthToken())

          val status = billingPage.waitForCreateCompleted(billingProjectName)
          withClue(s"Creating billing project: $billingProjectName") { status shouldEqual "success" }
        }

        //note that this next part of the test *could* be a different test on it's own, but we have chosen to
        //build it into this test case because:
        // a) having it in it's own test case would add another ~5 minutes to the test cycle
        // b) this test would fail anyway if billing project creation failed

        //should you decide to break this out into it's own test, billing project creation for it should
        //be driven by API and not UI

        val originalBillingAccount = Google.billing.getBillingProjectAccount(billingProjectName)
        originalBillingAccount shouldBe Some(Config.Projects.billingAccountId)

        Google.billing.removeBillingProjectAccount(billingProjectName)

        val newBillingAccount = Google.billing.getBillingProjectAccount(billingProjectName)
        newBillingAccount shouldBe None
      }

      "with a new billing project" - {

        "should be able to add a user to the billing project" in {
          val ownerUser = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = ownerUser.makeAuthToken()
          val secondUser = UserPool.chooseStudent.email

          withBillingProject("billing-spec-add-user") { billingProjectName =>
            withWebDriver { implicit driver =>
              withSignIn(ownerUser) { _ =>
                val billingPage = new BillingManagementPage().open
                billingPage.openBillingProject(billingProjectName)
                billingPage.addUserToBillingProject(secondUser, "User")

                val isSuccess = billingPage.isUserInBillingProject(secondUser)
                isSuccess shouldEqual true
              }
            }
          }
        }

        "should be able to create a workspace in the billing project" in {
          // Create new billing project
          val user = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = user.makeAuthToken()

          withBillingProject("billing-spec-make-ws") { billingProjectName =>
            withWebDriver { implicit driver =>
              withSignIn(user) { listPage =>
                // create workspace and verify
                val workspaceName = "BillingSpec_makeWorkspace_" + randomUuid
                register cleanUp api.workspaces.delete(billingProjectName, workspaceName)
                val detailPage = listPage.createWorkspace(billingProjectName, workspaceName)
              }
            }
          }
        }

        "should be able to run a method in a new workspace in the billing project" in {
          val user = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = user.makeAuthToken()

          /*
           * This must continue to create a new billing project rather than using an allocated one.
           * Otherwise it's not covering the entirety of the intended scenario (which is one that we
           * have seen break in the past that relied on using a brand new billing project).
           */
          withBillingProject("billing-spec-method") { billingProjectName =>
            withWorkspace(billingProjectName, "BillingSpec_runMethod") { workspaceName =>
              api.workspaces.waitForBucketReadAccess(billingProjectName, workspaceName)
              api.importMetaData(billingProjectName, workspaceName, "entities", TestData.SingleParticipant.participantEntity)

              val methodConfigName: String = "test_method" + UUID.randomUUID().toString
              api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProjectName, workspaceName, SimpleMethodConfig.configNamespace,
                SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

              withWebDriver { implicit driver =>
                withSignIn(user) { _ =>
                  // verify running a method
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
    }
  }
}
