package org.broadinstitute.dsde.firecloud.test.e2e

import java.util.UUID

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.fixture.{MethodData, SimpleMethodConfig, TestData, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.page.workspaces.{WorkspaceDataPage, WorkspaceSummaryPage}
import org.broadinstitute.dsde.firecloud.test.WebBrowserSpec
import org.scalatest._
import org.broadinstitute.dsde.firecloud.test.Tags

class SmoketestSpec extends FreeSpec with WebBrowserSpec with WorkspaceFixtures with Matchers {

  val billingProject: String = Config.Projects.smoketestBillingProject

  "Smoketest 1:  Log in, create workspace, import data, import method config, run method config, delete workspace" taggedAs Tags.ProdTest in withWebDriver { implicit driver =>

    // login
    var listPageAsUser = signIn(Config.Users.smoketestuser)
    assert(listPageAsUser.readUserEmail().equals(Config.Users.smoketestuser.email))

    // create workspace
    val workspaceName = "Smoketests_create_" + randomUuid
    val detailPage = listPageAsUser.createWorkspace(billingProject, workspaceName).awaitLoaded()

    detailPage.ui.readWorkspace shouldEqual (billingProject, workspaceName)
    listPageAsUser.open
    listPageAsUser.filter(workspaceName)
    listPageAsUser.ui.hasWorkspace(billingProject, workspaceName) shouldBe true

    // upload data set
    val filename = "src/test/resources/participants.txt"
    val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
    workspaceDataTab.importFile(filename)
    workspaceDataTab.getNumberOfParticipants() shouldEqual 1

    // import known method config
    val methodConfigName = Config.Methods.testMethodConfig + "_" + UUID.randomUUID().toString
    val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(Config.Methods.methodConfigNamespace,
      Config.Methods.testMethodConfig, Config.Methods.snapshotID, methodConfigName)

    assert(methodConfigDetailsPage.isLoaded)

    // launch method config with call caching off
    val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, enableCallCaching=false).awaitLoaded()
    submissionDetailsPage.waitUntilSubmissionCompletes() //This feels like the wrong way to do this?
    assert(submissionDetailsPage.verifyWorkflowSucceeded())

    // delete workspace
    val wsSummaryPage = new WorkspaceSummaryPage(billingProject, workspaceName).open.awaitLoaded()
    wsSummaryPage.deleteWorkspace()

  }

}