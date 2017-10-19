package org.broadinstitute.dsde.firecloud.test.billing

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.Rawls
import org.broadinstitute.dsde.firecloud.config.{AuthToken, UserPool, Config}
import org.broadinstitute.dsde.firecloud.fixture.{MethodData, SimpleMethodConfig, TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.billing.BillingManagementPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Ignore, Matchers}

/**
  * Tests related to billing accounts.
  */
@Ignore
class BillingSpec extends FreeSpec with WebBrowserSpec with UserFixtures with CleanUp
  with Matchers with LazyLogging {

  "A user" - {
    "with a billing account" - {
      "should be able to create a billing project" in withWebDriver { implicit driver =>
        val userOwner = UserPool.chooseProjectOwner
        implicit val authToken: AuthToken = AuthToken(userOwner)
        signIn(userOwner)

        val billingPage = new BillingManagementPage().open
        val billingProjectName = "billing-spec-create-" + makeRandomId() // is this a unique ID?
        logger.info(s"Creating billing project: $billingProjectName")

        billingPage.createBillingProject(billingProjectName, Config.Projects.billingAccount)
        register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthToken(UserPool.chooseAdmin))

        val status = billingPage.waitForCreateCompleted(billingProjectName)
        withClue(s"Creating billing project: $billingProjectName") { status shouldEqual "success" }
      }

      "with a new billing project" - {

        "should be able to add a user to the billing project" in withWebDriver { implicit driver =>
          val ownerUser = UserPool.chooseProjectOwner
          implicit val authToken: AuthToken = AuthToken(ownerUser)
          val secondUser = UserPool.chooseStudent.email

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-add-user-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthToken(UserPool.chooseAdmin)
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
          implicit val authToken: AuthToken = AuthToken(user)

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-make-ws-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthToken(UserPool.chooseAdmin)
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
          implicit val authToken: AuthToken = AuthToken(user)

          // TODO: extract this to BillingFixtures.withBillingProject
          val billingProjectName = "billing-spec-method-" + makeRandomId()
          logger.info(s"Creating billing project: $billingProjectName")
          register cleanUp Rawls.admin.deleteBillingProject(billingProjectName)(AuthToken(UserPool.chooseAdmin)
          api.billing.createBillingProject(billingProjectName, Config.Projects.billingAccountId)

          // create workspace
          val workspaceName = "BillingSpec_runMethod_" + randomUuid
          register cleanUp api.workspaces.delete(billingProjectName, workspaceName)
          api.workspaces.create(billingProjectName, workspaceName)

          api.importMetaData(billingProjectName, workspaceName, "entities", TestData.SingleParticipant.participantEntity)

          // verify running a method
          withSignIn(user) { _ =>
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
}
