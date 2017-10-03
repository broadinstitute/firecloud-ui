package org.broadinstitute.dsde.firecloud.test.analysis

import java.util.UUID

import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config, Credentials}
import org.broadinstitute.dsde.firecloud.fixture.{MethodData, MethodFixtures, TestData, WorkspaceFixtures}
import org.broadinstitute.dsde.firecloud.page.{ErrorModal, MessageModal}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.{WorkspaceMethodConfigDetailsPage, WorkspaceMethodConfigListPage}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest._

class MethodConfigSpec extends FreeSpec with WebBrowserSpec with CleanUp with WorkspaceFixtures with MethodFixtures {

  val billingProject: String = Config.Projects.default
  val methodName: String = MethodData.SimpleMethod.methodName + "_" + UUID.randomUUID().toString
  val methodConfigName: String = MethodData.SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString
  val wrongRootEntityErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type participant."
  val noExpressionErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type sample_set."
  val missingInputsErrorText: String = "is missing definitions for these inputs:"

  implicit val authToken: AuthToken = AuthTokens.hermione
  val uiUser: Credentials = Config.Users.hermione

  "launch a simple workflow" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_a_simple_workflow") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, MethodData.SimpleMethodConfig.configNamespace, methodConfigName)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodConfigName).open

      val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)

      submissionDetailsPage.waitUntilSubmissionCompletes() //This feels like the wrong way to do this?
      assert(submissionDetailsPage.verifyWorkflowSucceeded())
    }
  }

  "launch modal with no default entities" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_modal_no_default_entities") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, MethodData.SimpleMethodConfig.configNamespace, methodConfigName)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodConfigName).open

      methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("participant_set"))
      val launchModal = methodConfigDetailsPage.openlaunchModal()
      assert(launchModal.verifyNoDefaultEntityMessage())
      launchModal.closeModal()
    }
  }

  "launch modal with workflows warning" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_modal_with_workflows_warning") { workspaceName =>

      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, MethodData.SimpleMethodConfig.configNamespace, methodConfigName)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodConfigName).open
      methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

      val launchModal = methodConfigDetailsPage.openlaunchModal()
      launchModal.filterRootEntityType("sample_set")
      launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
      assert(launchModal.verifyWorkflowsWarning())
      launchModal.closeModal()
    }
  }

  "launch workflow with wrong root entity" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_with_wrong_root_entity") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, MethodData.SimpleMethodConfig.configNamespace, methodConfigName)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodConfigName).open
      methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

      val launchModal = methodConfigDetailsPage.openlaunchModal()
      launchModal.filterRootEntityType("participant")
      launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
      launchModal.clickLaunchButton()
      assert(launchModal.verifyWrongEntityError(wrongRootEntityErrorText))
      launchModal.closeModal()
    }
  }

  "launch workflow on set without expression" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_on_set_without_expression") { workspaceName =>

      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, MethodData.SimpleMethodConfig.configNamespace, methodConfigName)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodConfigName).open
      methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

      val launchModal = methodConfigDetailsPage.openlaunchModal()
      launchModal.filterRootEntityType("sample_set")
      launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
      launchModal.clickLaunchButton()
      assert(launchModal.verifyWrongEntityError(noExpressionErrorText))
      launchModal.closeModal()
    }
  }

  "launch workflow with input not defined" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_input_not_defined") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)

      signIn(uiUser)
      val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName)
      workspaceMethodConfigPage.open
      val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(MethodData.InputRequiredMethod.methodNamespace,
        MethodData.InputRequiredMethod.methodName, MethodData.InputRequiredMethod.snapshotId, methodConfigName, Some(MethodData.InputRequiredMethod.rootEntityType))

      val launchModal = methodConfigDetailsPage.openlaunchModal()
      launchModal.filterRootEntityType(MethodData.InputRequiredMethod.rootEntityType)
      launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
      launchModal.clickLaunchButton()
      assert(launchModal.verifyMissingInputsError(missingInputsErrorText))
      launchModal.closeModal()
    }
  }

  // This testcase requires pulling in new code from develop branch
  "import a method config from a workspace" in withWebDriver { implicit driver =>

  }

  "import a method config into a workspace from the method repo" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_import_method_config_from_workspace") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)

      signIn(uiUser)
      val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
      val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, methodConfigName)
      //    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs)) // not needed for config

      assert(methodConfigDetailsPage.isLoaded)
    }
  }

  "import a method into a workspace from the method repo" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_import_method_from_workspace") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)

      signIn(uiUser)
      val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
      val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(MethodData.SimpleMethod.methodNamespace,
        MethodData.SimpleMethod.methodName, MethodData.SimpleMethod.snapshotId, methodName, Some(MethodData.SimpleMethod.rootEntityType))
      methodConfigDetailsPage.editMethodConfig(inputs = Some(MethodData.SimpleMethodConfig.inputs))

      assert(methodConfigDetailsPage.isLoaded)
    }
  }

  "launch a method config from the method repo" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_method_config_from_workspace") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, MethodData.SimpleMethodConfig.configNamespace, methodConfigName)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodConfigName).open
      val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)

      submissionDetailsPage.waitUntilSubmissionCompletes()
      assert(submissionDetailsPage.verifyWorkflowSucceeded())
    }
  }

  "launch a method from the method repo" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_method_from_workspace") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      val res = api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, 1,
        MethodData.SimpleMethod.methodNamespace, MethodData.SimpleMethod.methodName, MethodData.SimpleMethod.snapshotId,
        MethodData.SimpleMethodConfig.configNamespace, methodName,  MethodData.SimpleMethodConfig.inputs,
        MethodData.SimpleMethodConfig.outputs, MethodData.SimpleMethod.rootEntityType)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodName).open
      val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, TestData.SingleParticipant.entityId)

      submissionDetailsPage.waitUntilSubmissionCompletes()
      assert(submissionDetailsPage.verifyWorkflowSucceeded())
    }
  }

  "abort a workflow" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_abort_workflow") { workspaceName =>
      val shouldUseCallCaching = false
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, MethodData.SimpleMethodConfig.configNamespace, methodConfigName)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodConfigName).open
      val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", shouldUseCallCaching)
      //TODO start the submission via API - reduce the amount of UI surface. - requires getting the submission ID
      submissionDetailsPage.abortSubmission()
      submissionDetailsPage.waitUntilSubmissionCompletes()
      assert(submissionDetailsPage.verifyWorkflowAborted())

    }
  }

  "delete a method config from a workspace" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_delete_method_from_workspace") { workspaceName =>
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace,
        MethodData.SimpleMethodConfig.configName, MethodData.SimpleMethodConfig.snapshotId, MethodData.SimpleMethodConfig.configNamespace, methodConfigName)

      signIn(uiUser)
      val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, methodConfigName).open
      val workspaceMethodConfigPage = methodConfigDetailsPage.deleteMethodConfig()

      workspaceMethodConfigPage.filter(methodConfigName)
      assert(find(methodConfigName).isEmpty)
    }
  }

  "For a method config that references a redacted method" - {
    "should be able to choose new method snapshot" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "MethodConfigTabSpec_redacted_choose_new_snapshot") { workspaceName =>
        withConfigForRedactedMethodInWorkspace("MethodConfigTabSpec_redacted_choose_new_snapshot", billingProject, workspaceName, true) { configName =>
          signIn(uiUser)
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, configName).open
          assert(methodConfigDetailsPage.ui.isSnapshotRedacted())

          methodConfigDetailsPage.ui.openEditMode()
          methodConfigDetailsPage.ui.saveEdits("disabled")
          val messageModal = MessageModal()
          assert(messageModal.validateLocation)
          messageModal.clickCancel()

          methodConfigDetailsPage.ui.changeSnapshotId(2)
          methodConfigDetailsPage.ui.saveEdits()
          assert(!methodConfigDetailsPage.ui.isSnapshotRedacted())
        }
      }
    }
    "launch analysis button should be disabled and show error if clicked" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "MethodConfigTabSpec_redacted_launch_analysis_error") { workspaceName =>
        withConfigForRedactedMethodInWorkspace("MethodConfigTabSpec_redacted_launch_analysis_error", billingProject, workspaceName, true) { configName =>
          signIn(uiUser)
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, configName).open
          assert(methodConfigDetailsPage.ui.isSnapshotRedacted())

          methodConfigDetailsPage.ui.openLaunchAnalysisModal()
          val messageModalModal = MessageModal()
          assert(messageModalModal.validateLocation)
          messageModalModal.clickCancel()
        }
      }
    }
    "should be able to be deleted when no unredacted snapshot exists" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "MethodConfigTabSpec_redacted_delete") { workspaceName =>
        withConfigForRedactedMethodInWorkspace("MethodConfigTabSpec_redacted_delete", billingProject, workspaceName, false) { configName =>
          signIn(uiUser)
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, MethodData.SimpleMethodConfig.configNamespace, configName).open
          assert(methodConfigDetailsPage.ui.isSnapshotRedacted())

          methodConfigDetailsPage.ui.openEditMode()
          val messageModal = MessageModal()
          assert(messageModal.validateLocation)
          messageModal.clickCancel()

          methodConfigDetailsPage.ui.deleteMethodConfig()
          val list = new WorkspaceMethodConfigListPage(billingProject, workspaceName)
          assert(!list.ui.hasConfig(configName))
        }
      }
    }
  }


  // negative tests

}
