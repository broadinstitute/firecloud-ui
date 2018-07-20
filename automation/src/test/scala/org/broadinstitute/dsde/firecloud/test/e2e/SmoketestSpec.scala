package org.broadinstitute.dsde.firecloud.test.e2e

import java.util.UUID

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.test.Tags
import org.broadinstitute.dsde.workbench.fixture.{SimpleMethodConfig, TestReporterFixture, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._
import org.scalatest.time.{Millis, Seconds, Span}

class SmoketestSpec extends FreeSpec with WebBrowserSpec
  with UserFixtures with WorkspaceFixtures with Matchers with TestReporterFixture {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  val billingProject: String = FireCloudConfig.Projects.smoketestBillingProject

  "Smoketest 1:  Log in, create workspace, import data, import method config, run method config, delete workspace" taggedAs Tags.ProdTest in withWebDriver { implicit driver =>

    // login
    withSignInReal(FireCloudConfig.Users.smoketestuser) { listPageAsUser =>

      eventually { listPageAsUser.readUserEmail() shouldEqual FireCloudConfig.Users.smoketestuser.email }

      // create workspace
      val workspaceName = "Smoketests_create_" + randomUuid
      val detailPage = listPageAsUser.createWorkspace(billingProject, workspaceName)

      eventually { detailPage.readWorkspace shouldEqual (billingProject, workspaceName) }
      listPageAsUser.open
      listPageAsUser.hasWorkspace(billingProject, workspaceName) shouldBe true

      // upload data set
      val filename = "src/test/resources/participants.txt"
      val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
      workspaceDataTab.importFile(filename)
      eventually { workspaceDataTab.getNumberOfParticipants shouldEqual 1 }

      // import known method config
      val methodConfigName = FireCloudConfig.Methods.testMethodConfig + "_" + UUID.randomUUID().toString
      val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
      val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(FireCloudConfig.Methods.methodConfigNamespace,
        FireCloudConfig.Methods.testMethodConfig, FireCloudConfig.Methods.snapshotID, methodConfigName)

      assert(methodConfigDetailsPage.isLoaded)

      // launch method config with call caching off
      val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData().participantId, enableCallCaching=false)
      submissionDetailsPage.waitUntilSubmissionCompletes() //This feels like the wrong way to do this?
      assert(submissionDetailsPage.verifyWorkflowSucceeded())

      // delete workspace
      val wsSummaryPage = new WorkspaceSummaryPage(billingProject, workspaceName).open
      wsSummaryPage.deleteWorkspace()
      listPageAsUser.validateLocation()
      listPageAsUser.hasWorkspace(billingProject, workspaceName) shouldBe false
    }
  }

}
