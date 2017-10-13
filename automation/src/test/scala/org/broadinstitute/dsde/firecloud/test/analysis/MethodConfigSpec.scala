package org.broadinstitute.dsde.firecloud.test.analysis

import java.util.UUID

import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config, Credentials}
import org.broadinstitute.dsde.firecloud.fixture.{TestData, _}
import org.broadinstitute.dsde.firecloud.page.MessageModal
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.{WorkspaceMethodConfigDetailsPage, WorkspaceMethodConfigListPage}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec, Tags}
import org.scalatest._


class MethodConfigSpec extends FreeSpec with WebBrowserSpec with CleanUp with WorkspaceFixtures with UserFixtures with MethodFixtures with Matchers {

  val billingProject: String = Config.Projects.default
  val methodName: String = MethodData.SimpleMethod.methodName + "_" + UUID.randomUUID().toString
  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString
  val wrongRootEntityErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type participant."
  val noExpressionErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type sample_set."
  val missingInputsErrorText: String = "is missing definitions for these inputs:"

  implicit lazy val authToken: AuthToken = AuthTokens.hermione
  val uiUser: Credentials = Config.Users.hermione

  "launch a simple workflow" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_a_simple_workflow") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open

        val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)

        submissionDetailsPage.waitUntilSubmissionCompletes() //This feels like the wrong way to do this?
        submissionDetailsPage.readWorkflowStatus() shouldBe submissionDetailsPage.SUCCESS_STATUS
      }

    }
  }

  "launch modal with no default entities" ignore withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_modal_no_default_entities") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open

        methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("participant_set"))
        val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
        launchModal.verifyNoDefaultEntityMessage() shouldBe true
        launchModal.xOut()
      }
    }
  }

  "launch modal with workflows warning" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_modal_with_workflows_warning") { workspaceName =>

      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

        val launchModal = methodConfigDetailsPage.openLaunchModal()
        launchModal.filterRootEntityType("sample_set")
        launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
        launchModal.verifyWorkflowsWarning() shouldBe true
        launchModal.xOut()
      }
    }
  }

  "launch workflow with wrong root entity" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_with_wrong_root_entity") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

        val launchModal = methodConfigDetailsPage.openLaunchModal()
        launchModal.filterRootEntityType("participant")
        launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
        launchModal.clickLaunchButton()
        launchModal.verifyWrongEntityError(wrongRootEntityErrorText)  shouldBe true
        launchModal.xOut()
      }
    }
  }

  "launch workflow on set without expression" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_on_set_without_expression") { workspaceName =>

      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
      api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

        val launchModal = methodConfigDetailsPage.openLaunchModal()
        launchModal.filterRootEntityType("sample_set")
        launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
        launchModal.clickLaunchButton()
        launchModal.verifyWrongEntityError(noExpressionErrorText) shouldBe true
        launchModal.xOut()
      }
    }
  }

  "launch workflow with input not defined" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_input_not_defined") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      val method = MethodData.InputRequiredMethod
      api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, method,
        method.methodNamespace, methodConfigName, 1,
        Map.empty, Map.empty, method.rootEntityType)

      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, method.methodNamespace, methodConfigName).open

        val launchModal = methodConfigDetailsPage.openLaunchModal()
        launchModal.filterRootEntityType(method.rootEntityType)
        launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
        launchModal.clickLaunchButton()
        launchModal.verifyMissingInputsError(missingInputsErrorText) shouldBe true
        launchModal.xOut()
      }
    }
  }

  // This testcase requires pulling in new code from develop branch
  "import a method config from a workspace" in withWebDriver { implicit driver =>

  }

  "import a method config into a workspace from the method repo" ignore withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_import_method_config_from_workspace") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      withSignIn(uiUser) { _ =>
        val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
        val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, methodConfigName)
        //    methodConfigDetailsPage.editMethodConfig(inputs = Some(TestData.SimpleMethodConfig.inputs)) // not needed for config

        methodConfigDetailsPage.isLoaded shouldBe true
      }
    }
  }

  "import a method into a workspace from the method repo" ignore withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_import_method_from_workspace") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      withSignIn(uiUser) { _ =>
        val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
        val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(MethodData.SimpleMethod.methodNamespace,
          MethodData.SimpleMethod.methodName, MethodData.SimpleMethod.snapshotId, methodName, Some(MethodData.SimpleMethod.rootEntityType))
        methodConfigDetailsPage.editMethodConfig(inputs = Some(SimpleMethodConfig.inputs))

        methodConfigDetailsPage.isLoaded shouldBe true
      }
    }
  }

  "launch a method config from the method repo" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_method_config_from_workspace") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)

        submissionDetailsPage.waitUntilSubmissionCompletes()
        submissionDetailsPage.verifyWorkflowSucceeded() shouldBe true
      }
    }
  }

  "launch a method from the method repo" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_method_from_workspace") { workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName,
        MethodData.SimpleMethod, SimpleMethodConfig.configNamespace, methodName, 1,
        SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, MethodData.SimpleMethod.rootEntityType)

      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodName).open
        val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, TestData.SingleParticipant.entityId)

        submissionDetailsPage.waitUntilSubmissionCompletes()
        submissionDetailsPage.verifyWorkflowSucceeded() shouldBe true
      }
    }
  }

  "abort a workflow" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_abort_workflow") { workspaceName =>
      val shouldUseCallCaching = false
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", shouldUseCallCaching)
        //TODO start the submission via API - reduce the amount of UI surface. - requires getting the submission ID
        submissionDetailsPage.abortSubmission()
        submissionDetailsPage.waitUntilSubmissionCompletes()
        submissionDetailsPage.verifyWorkflowAborted() shouldBe true
      }
    }
  }

  "delete a method config from a workspace" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_delete_method_from_workspace") { workspaceName =>
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

      withSignIn(uiUser) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        val workspaceMethodConfigPage = methodConfigDetailsPage.deleteMethodConfig()
        workspaceMethodConfigPage.hasConfig(methodConfigName) shouldBe false
      }
    }
  }

  "For a method config that references a redacted method" - {
    "should be able to choose new method snapshot" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "MethodConfigTabSpec_redacted_choose_new_snapshot") { workspaceName =>
        withMethod("MethodConfigTabSpec_redacted_choose_new_snapshot", MethodData.SimpleMethod, 2) { methodName =>
          val configName = methodName + "Config"
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
            SimpleMethodConfig.configNamespace, configName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
          api.methods.redact(MethodData.SimpleMethod.copy(methodName = methodName))

          withSignIn(uiUser) { _ =>
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, configName).open
            methodConfigDetailsPage.openEditMode()
            methodConfigDetailsPage.checkSaveButtonState shouldEqual "disabled"
            methodConfigDetailsPage.saveEdits(expectSuccess = false)
            val modal = MessageModal()
            modal.validateLocation shouldBe true
            modal.clickCancel()

            methodConfigDetailsPage.changeSnapshotId(2)
            methodConfigDetailsPage.saveEdits()
            methodConfigDetailsPage.isSnapshotRedacted shouldBe false
          }
        }
      }
    }
    "should have warning icon in config list" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "MethodConfigTabSpec_redacted_launch_analysis_error") { workspaceName =>
        withMethod("MethodConfigTabSpec_redacted_has_warning_icon", MethodData.SimpleMethod) { methodName =>
          val configName = methodName + "Config"
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
            SimpleMethodConfig.configNamespace, configName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")

          api.methods.redact(MethodData.SimpleMethod.copy(methodName = methodName))

          withSignIn(uiUser) { _ =>
            val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
            workspaceMethodConfigPage.hasRedactedIcon(configName) shouldBe true
          }
        }
      }
    }
    "launch analysis button should be disabled and show error if clicked" ignore withWebDriver { implicit driver =>
      withWorkspace(billingProject, "MethodConfigTabSpec_redacted_launch_analysis_error") { workspaceName =>
        withMethod("MethodConfigTabSpec_redacted_launch_analysis_error", MethodData.SimpleMethod) { methodName =>
          val configName = methodName + "Config"
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
            SimpleMethodConfig.configNamespace, configName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
          api.methods.redact(MethodData.SimpleMethod.copy(methodName = methodName))

          withSignIn(uiUser){ _ =>
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, configName).open

          val modal = methodConfigDetailsPage.clickLaunchAnalysisButtonError()
          modal.validateLocation shouldBe true
          modal.clickCancel()}
        }
      }
    }
    "should be able to be deleted when no unredacted snapshot exists" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "MethodConfigTabSpec_redacted_delete") { workspaceName =>
        withMethod("MethodConfigTabSpec_redacted_delete", MethodData.SimpleMethod) { methodName =>
          val configName = methodName + "Config"
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
            SimpleMethodConfig.configNamespace, configName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
          api.methods.redact(MethodData.SimpleMethod.copy(methodName = methodName))

          withSignIn(uiUser) { _ =>
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, configName).open
            methodConfigDetailsPage.openEditMode(expectSuccess = false)
            val modal = MessageModal()
            modal.validateLocation shouldBe true
            modal.clickCancel()

            methodConfigDetailsPage.deleteMethodConfig()
            val list = await ready new WorkspaceMethodConfigListPage(billingProject, workspaceName)
            list.hasConfig(configName) shouldBe false
          }
        }
      }
    }
  }


  // negative tests

}
