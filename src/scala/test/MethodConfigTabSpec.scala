import java.util.UUID

import org.broadinstitute.dsde.firecloud.Config
import org.broadinstitute.dsde.firecloud.api.Orchestration
import org.broadinstitute.dsde.firecloud.data.TestData
import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceListPage, WorkspaceMethodConfigPage, WorkspaceSummaryPage}
import org.scalatest._

class MethodConfigTabSpec extends FreeSpec with WebBrowserSpec with BeforeAndAfterAll with BeforeAndAfterEach {

  val wsNamespace = "broad-dsde-dev"
  val methodConfigName = "test_method" + UUID.randomUUID().toString
  val wrongRootEntityErrorText = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type participant."
  val noExpressionErrorText = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type sample_set."


  "launch a simple workflow" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_a_simple_workflow" + UUID.randomUUID.toString
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.create(wsNamespace, wsName)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.SingleParticipant.participantEntity)

    signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(wsNamespace, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfig(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, TestData.SimpleMethodConfig.rootEntityType)
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))
    val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(TestData.SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)

    submissionDetailsPage.waitUntilSubmissionCompletes()  //This feels like the wrong way to do this?
    assert(submissionDetailsPage.verifyWorkflowSucceeded())
    val submissionSummaryPage = new WorkspaceSummaryPage(wsNamespace, wsName)
    submissionSummaryPage.open
    submissionSummaryPage.deleteWorkspace()
  }

  "launch modal with no default entities" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_modal_no_default_entities" + UUID.randomUUID.toString
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.create(wsNamespace, wsName)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.SingleParticipant.participantEntity)

    signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(wsNamespace, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfig(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, "participant_set")
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))
    val launchModal = methodConfigDetailsPage.openlaunchModal()
    assert(launchModal.verifyNoDefaultEntityMessage())
    launchModal.closeModal()

    val submissionSummaryPage = new WorkspaceSummaryPage(wsNamespace, wsName)
    submissionSummaryPage.open
    submissionSummaryPage.deleteWorkspace()
  }

  "launch modal with workflows warning" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_modal_with_workflows_warning" + UUID.randomUUID.toString
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.create(wsNamespace, wsName)

    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.SingleParticipant.participantEntity)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.HundredAndOneSampleSet.samples)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)

    signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(wsNamespace, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfig(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, "sample")
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))

    val launchModal = methodConfigDetailsPage.openlaunchModal()
    launchModal.filterRootEntityType("sample_set")
    launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
    assert(launchModal.verifyWorkflowsWarning())
    launchModal.closeModal()

    val submissionSummaryPage = new WorkspaceSummaryPage(wsNamespace, wsName)
    submissionSummaryPage.open
    submissionSummaryPage.deleteWorkspace()
  }

  "launch workflow with wrong root entity" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_workflow_with_wrong_root_entity" + UUID.randomUUID.toString
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.create(wsNamespace, wsName)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.SingleParticipant.participantEntity)

    signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)
    val workspaceListPage = new WorkspaceListPage
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(wsNamespace, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfig(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, "sample")
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))

    val launchModal = methodConfigDetailsPage.openlaunchModal()
    launchModal.filterRootEntityType("participant")
    launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
    launchModal.clickLaunchButton()
    assert(launchModal.verifyWrongEntityError(wrongRootEntityErrorText))
    launchModal.closeModal()

    val submissionSummaryPage = new WorkspaceSummaryPage(wsNamespace, wsName)
    submissionSummaryPage.open
    submissionSummaryPage.deleteWorkspace()
  }

  "launch workflow on set without expression" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_workflow_on_set_without_expression" + UUID.randomUUID.toString
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.create(wsNamespace, wsName)

    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.SingleParticipant.participantEntity)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.HundredAndOneSampleSet.samples)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)

    signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(wsNamespace, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfig(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, "sample")
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))

    val launchModal = methodConfigDetailsPage.openlaunchModal()
    launchModal.filterRootEntityType("sample_set")
    launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
    launchModal.clickLaunchButton()
    assert(launchModal.verifyWrongEntityError(noExpressionErrorText))
    launchModal.closeModal()

    val submissionSummaryPage = new WorkspaceSummaryPage(wsNamespace, wsName)
    submissionSummaryPage.open
    submissionSummaryPage.deleteWorkspace()

  }

  "launch workflow with input not defined" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_workflow_input_not_defined" + UUID.randomUUID.toString
    implicit val authToken = Config.AuthTokens.testFireC
    Orchestration.workspaces.create(wsNamespace, wsName)
    Orchestration.importMetaData(wsNamespace, wsName, "entities", TestData.SingleParticipant.participantEntity)

    signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(wsNamespace, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfig(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, TestData.SimpleMethodConfig.rootEntityType)
    val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(TestData.SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)
    assert(submissionDetailsPage.verifyWorkflowFailed())

    val submissionSummaryPage = new WorkspaceSummaryPage(wsNamespace, wsName)
    submissionSummaryPage.open
    submissionSummaryPage.deleteWorkspace()
  }


}