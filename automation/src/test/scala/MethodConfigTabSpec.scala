import java.util.UUID

import org.broadinstitute.dsde.firecloud.auth.{AuthToken, AuthTokens, Credentials}
import org.broadinstitute.dsde.firecloud.data.TestData
import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceListPage, WorkspaceMethodConfigPage}
import org.broadinstitute.dsde.firecloud.{CleanUp, Config}
import org.scalatest._

class MethodConfigTabSpec extends FreeSpec with WebBrowserSpec with CleanUp {

  val billingProject: String = Config.Projects.default
  val methodConfigName: String = "test_method" + UUID.randomUUID().toString
  val wrongRootEntityErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type participant."
  val noExpressionErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type sample_set."
  val missingInputsErrorText: String = "is missing definitions for these inputs:"

  implicit val authToken: AuthToken = AuthTokens.hermione
  val uiUser: Credentials = Config.Users.hermione

  "launch a simple workflow" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_a_simple_workflow" + UUID.randomUUID.toString
    api.workspaces.create(billingProject, wsName)
    register cleanUp api.workspaces.delete(billingProject, wsName)
    api.importMetaData(billingProject, wsName, "entities", TestData.SingleParticipant.participantEntity)

    signIn(uiUser)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(billingProject, wsName).open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, TestData.SimpleMethodConfig.rootEntityType)
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))
    val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(TestData.SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)

    submissionDetailsPage.waitUntilSubmissionCompletes()  //This feels like the wrong way to do this?
    assert(submissionDetailsPage.verifyWorkflowSucceeded())
  }

  "launch modal with no default entities" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_modal_no_default_entities" + UUID.randomUUID.toString
    api.workspaces.create(billingProject, wsName)
    register cleanUp api.workspaces.delete(billingProject, wsName)
    api.importMetaData(billingProject, wsName, "entities", TestData.SingleParticipant.participantEntity)

    signIn(uiUser)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(billingProject, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, "participant_set")
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))
    val launchModal = methodConfigDetailsPage.openlaunchModal()
    assert(launchModal.verifyNoDefaultEntityMessage())
    launchModal.closeModal()
  }

  "launch modal with workflows warning" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_modal_with_workflows_warning" + UUID.randomUUID.toString
    api.workspaces.create(billingProject, wsName)
    register cleanUp api.workspaces.delete(billingProject, wsName)

    api.importMetaData(billingProject, wsName, "entities", TestData.SingleParticipant.participantEntity)
    api.importMetaData(billingProject, wsName, "entities", TestData.HundredAndOneSampleSet.samples)
    api.importMetaData(billingProject, wsName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
    api.importMetaData(billingProject, wsName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)

    signIn(uiUser)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(billingProject, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, "sample")
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))

    val launchModal = methodConfigDetailsPage.openlaunchModal()
    launchModal.filterRootEntityType("sample_set")
    launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
    assert(launchModal.verifyWorkflowsWarning())
    launchModal.closeModal()
  }

  "launch workflow with wrong root entity" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_workflow_with_wrong_root_entity" + UUID.randomUUID.toString
    api.workspaces.create(billingProject, wsName)
    register cleanUp api.workspaces.delete(billingProject, wsName)
    api.importMetaData(billingProject, wsName, "entities", TestData.SingleParticipant.participantEntity)

    signIn(uiUser)
    val workspaceListPage = new WorkspaceListPage
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(billingProject, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, "sample")
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))

    val launchModal = methodConfigDetailsPage.openlaunchModal()
    launchModal.filterRootEntityType("participant")
    launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
    launchModal.clickLaunchButton()
    assert(launchModal.verifyWrongEntityError(wrongRootEntityErrorText))
    launchModal.closeModal()
  }

  "launch workflow on set without expression" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_workflow_on_set_without_expression" + UUID.randomUUID.toString

    api.workspaces.create(billingProject, wsName)
    register cleanUp api.workspaces.delete(billingProject, wsName)

    api.importMetaData(billingProject, wsName, "entities", TestData.SingleParticipant.participantEntity)
    api.importMetaData(billingProject, wsName, "entities", TestData.HundredAndOneSampleSet.samples)
    api.importMetaData(billingProject, wsName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
    api.importMetaData(billingProject, wsName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)

    signIn(uiUser)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(billingProject, wsName).open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(TestData.SimpleMethodConfig.namespace,
      TestData.SimpleMethodConfig.name, TestData.SimpleMethodConfig.snapshotId, methodConfigName, "sample")
    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs))

    val launchModal = methodConfigDetailsPage.openlaunchModal()
    launchModal.filterRootEntityType("sample_set")
    launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
    launchModal.clickLaunchButton()
    assert(launchModal.verifyWrongEntityError(noExpressionErrorText))
    launchModal.closeModal()
  }

  "launch workflow with input not defined" in withWebDriver { implicit driver =>
    val wsName = "TestSpec_FireCloud_launch_workflow_input_not_defined" + UUID.randomUUID.toString
    api.workspaces.create(billingProject, wsName)
    register cleanUp api.workspaces.delete(billingProject, wsName)
    api.importMetaData(billingProject, wsName, "entities", TestData.SingleParticipant.participantEntity)

    signIn(uiUser)
    val workspaceMethodConfigPage = new WorkspaceMethodConfigPage(billingProject, wsName)
    workspaceMethodConfigPage.open
    val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(TestData.InputRequiredMethodConfig.namespace,
      TestData.InputRequiredMethodConfig.name, TestData.InputRequiredMethodConfig.snapshotId, methodConfigName, TestData.InputRequiredMethodConfig.rootEntityType)

    val launchModal = methodConfigDetailsPage.openlaunchModal()
    launchModal.filterRootEntityType(TestData.InputRequiredMethodConfig.rootEntityType)
    launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
    launchModal.clickLaunchButton()
    assert(launchModal.verifyMissingInputsError(missingInputsErrorText))
    launchModal.closeModal()
  }

  "import a method config from a workspace" in withWebDriver { implicit driver =>

  }

  "import a method config into a workspace from the method repo" in withWebDriver { implicit driver =>

  }

  // negative tests

  "delete a method config from a workspace" in withWebDriver { implicit driver =>

  }


}
