package org.broadinstitute.dsde.firecloud.test.analysis

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.component.MessageModal
import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures, _}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.{WorkspaceMethodConfigDetailsPage, WorkspaceMethodConfigListPage}
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._


class MethodConfigSpec extends FreeSpec with Matchers with LazyLogging
  with WebBrowserSpec with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures {

  val methodName: String = MethodData.SimpleMethod.methodName + "_" + UUID.randomUUID().toString
  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString
  val wrongRootEntityErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type participant."
  val noExpressionErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type sample_set."
  val missingInputsErrorText: String = "is missing definitions for these inputs:"

  "launch and delete a simple workflow" in withWebDriver { implicit driver =>
    val user = Config.Users.owner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_a_simple_workflow") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
        withSignIn(user) { _ =>
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open

          val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)

          submissionDetailsPage.waitUntilSubmissionCompletes()
          submissionDetailsPage.readWorkflowStatus() shouldBe submissionDetailsPage.SUCCESS_STATUS

          // should be able to delete workflow
          methodConfigDetailsPage.open
          val workspaceMethodConfigPage = methodConfigDetailsPage.deleteMethodConfig()
          workspaceMethodConfigPage.hasConfig(methodConfigName) shouldBe false
        }
      }
    }
  }

  "launch modal with workflows warning" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_modal_with_workflows_warning") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
        api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
        api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withSignIn(user) { _ =>
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
          methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

          val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
          launchModal.filterRootEntityType("sample_set")
          launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
          launchModal.verifyWorkflowsWarning() shouldBe true
          launchModal.xOut()
        }
      }
    }
  }

  "launch workflow with wrong root entity" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_with_wrong_root_entity") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withSignIn(user) { _ =>
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
          methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

          val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
          launchModal.filterRootEntityType("participant")
          launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
          launchModal.clickLaunchButton()
          launchModal.verifyErrorText(wrongRootEntityErrorText) shouldBe true
          launchModal.xOut()
        }
      }
    }
  }

  "launch workflow on set without expression" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_on_set_without_expression") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.samples)
        api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetCreation)
        api.importMetaData(billingProject, workspaceName, "entities", TestData.HundredAndOneSampleSet.sampleSetMembership)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withSignIn(user) { _ =>
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
          methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

          val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
          launchModal.filterRootEntityType("sample_set")
          launchModal.searchAndSelectEntity(TestData.HundredAndOneSampleSet.entityId)
          launchModal.clickLaunchButton()
          launchModal.verifyErrorText(noExpressionErrorText) shouldBe true
          launchModal.xOut()
        }
      }
    }
  }

  "launch workflow with input not defined" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_workflow_input_not_defined") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        val method = MethodData.InputRequiredMethod
        api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, method,
          method.methodNamespace, methodConfigName, 1,
          Map.empty, Map.empty, method.rootEntityType)

        withSignIn(user) { _ =>
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, method.methodNamespace, methodConfigName).open

          val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
          launchModal.filterRootEntityType(method.rootEntityType)
          launchModal.searchAndSelectEntity(TestData.SingleParticipant.entityId)
          launchModal.clickLaunchButton()
          launchModal.verifyErrorText(missingInputsErrorText) shouldBe true
          launchModal.xOut()
        }
      }
    }
  }

  "import a method config from a workspace" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "Test_copy_method_config_from_workspace_src") { sourceWorkspaceName =>
        withWorkspace(billingProject, "Test_copy_method_config_from_workspace_dest") { destWorkspaceName =>
          val method = MethodData.SimpleMethod
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, sourceWorkspaceName,
            method, method.methodNamespace, method.methodName, 1, Map.empty, Map.empty, method.rootEntityType)

          withSignIn(user) { listPage =>
            val methodConfigTab = listPage.enterWorkspace(billingProject, destWorkspaceName).goToMethodConfigTab()

            val methodConfigDetailsPage = methodConfigTab.copyMethodConfigFromWorkspace(
              billingProject, sourceWorkspaceName, method.methodNamespace, method.methodName)

            methodConfigDetailsPage.isLoaded shouldBe true
            methodConfigDetailsPage.methodConfigName shouldBe method.methodName

            // launch modal shows no default entities
            val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
            launchModal.verifyNoRowsMessage() shouldBe true
            launchModal.xOut()
          }
        }
      }
    }
  }

  "import a method config into a workspace from the method repo" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_import_method_config_from_workspace") { workspaceName =>
        withSignIn(user) { workspaceListPage =>
          val methodConfigPage = workspaceListPage.enterWorkspace(billingProject, workspaceName).goToMethodConfigTab()

          val methodConfigDetailsPage = methodConfigPage.importMethodConfigFromRepo(
            MethodData.SimpleMethod.methodNamespace,
            MethodData.SimpleMethod.methodName,
            MethodData.SimpleMethod.snapshotId,
            SimpleMethodConfig.configName)

          methodConfigDetailsPage.isLoaded shouldBe true
        }
      }
    }
  }

  "import a method into a workspace from the method repo" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_import_method_from_workspace") { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        withSignIn(user) { _ =>
          val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open

          val methodConfigDetailsPage = workspaceMethodConfigPage.importMethodConfigFromRepo(
            MethodData.SimpleMethod.methodNamespace,
            MethodData.SimpleMethod.methodName,
            MethodData.SimpleMethod.snapshotId,
            SimpleMethodConfig.configName,
            Some(MethodData.SimpleMethod.rootEntityType))

          methodConfigDetailsPage.editMethodConfig(inputs = Some(SimpleMethodConfig.inputs))

          methodConfigDetailsPage.isLoaded shouldBe true
        }
      }
    }
  }

  "launch a method from the method repo" in withWebDriver { implicit driver =>
    val user = Config.Users.owner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_method_from_workspace") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName,
          MethodData.SimpleMethod, SimpleMethodConfig.configNamespace, methodName, 1,
          SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, MethodData.SimpleMethod.rootEntityType)

        withSignIn(user) { _ =>
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodName).open
          val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, TestData.SingleParticipant.entityId)

          submissionDetailsPage.waitUntilSubmissionCompletes()
          submissionDetailsPage.verifyWorkflowSucceeded() shouldBe true
        }
      }
    }
  }

  "abort a submission" in withWebDriver { implicit driver =>
    val user = Config.Users.owner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_abort_submission") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

        val shouldUseCallCaching = false
        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName,
          MethodData.SimpleMethod, SimpleMethodConfig.configNamespace, methodName, 1,
          SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, MethodData.SimpleMethod.rootEntityType)

        withSignIn(user) { _ =>
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodName).open
          val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, TestData.SingleParticipant.entityId, "", shouldUseCallCaching)
          //TODO start the submission via API - reduce the amount of UI surface. - requires getting the submission ID
          submissionDetailsPage.abortSubmission()
          submissionDetailsPage.waitUntilSubmissionCompletes()
          submissionDetailsPage.getSubmissionStatus shouldBe submissionDetailsPage.ABORTED_STATUS
        }
      }
    }
  }

  "For a method config that references a redacted method" - {
    "should be able to choose new method snapshot" in withWebDriver { implicit driver =>
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "MethodConfigTabSpec_redacted_choose_new_snapshot") { workspaceName =>
          withMethod("MethodConfigTabSpec_redacted_choose_new_snapshot", MethodData.SimpleMethod, 2) { methodName =>
            val configName = methodName + "Config"
            api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
              SimpleMethodConfig.configNamespace, configName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
            api.methods.redact(MethodData.SimpleMethod.copy(methodName = methodName))

            withSignIn(user) { _ =>
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, configName).open
              methodConfigDetailsPage.openEditMode()
              methodConfigDetailsPage.checkSaveButtonState shouldEqual "disabled"
              methodConfigDetailsPage.saveEdits(expectSuccess = false)
              val modal = await ready new MessageModal()
              modal.isVisible shouldBe true
              modal.clickOk()

              methodConfigDetailsPage.changeSnapshotId(2)
              methodConfigDetailsPage.saveEdits()
              methodConfigDetailsPage.isSnapshotRedacted shouldBe false
            }
          }
        }
      }
    }

    "launch analysis error" in withWebDriver { implicit driver =>
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "MethodConfigTabSpec_redacted_launch_analysis_error") { workspaceName =>
          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

          withMethod("MethodConfigTabSpec_redacted_launch_analysis_error", MethodData.SimpleMethod) { methodName =>
            val configName = methodName + "Config"
            api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
              SimpleMethodConfig.configNamespace, configName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
            api.methods.redact(MethodData.SimpleMethod.copy(methodName = methodName))

            withSignIn(user) { _ =>
              // should have warning icon in config list
              val workspaceMethodConfigPage = new WorkspaceMethodConfigListPage(billingProject, workspaceName).open
              workspaceMethodConfigPage.hasRedactedIcon(configName) shouldBe true

              // should be disabled and show error if clicked
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, configName).open
              val modal = methodConfigDetailsPage.clickLaunchAnalysisButtonError()
              modal.isVisible shouldBe true
              modal.clickOk()
            }

          }
        }
      }
    }

    "should be able to be deleted when no unredacted snapshot exists" in withWebDriver { implicit driver =>
      val user = UserPool.chooseProjectOwner
      implicit val authToken: AuthToken = user.makeAuthToken()
      withCleanBillingProject(user) { billingProject =>
        withWorkspace(billingProject, "MethodConfigTabSpec_redacted_delete") { workspaceName =>
          withMethod("MethodConfigTabSpec_redacted_delete", MethodData.SimpleMethod) { methodName =>
            val configName = methodName + "Config"
            api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod.copy(methodName = methodName),
              SimpleMethodConfig.configNamespace, configName, 1, SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, "participant")
            api.methods.redact(MethodData.SimpleMethod.copy(methodName = methodName))

            withSignIn(user) { _ =>
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, configName).open
              methodConfigDetailsPage.openEditMode(expectSuccess = false)
              val modal = await ready new MessageModal()
              modal.isVisible shouldBe true
              modal.clickOk()

              methodConfigDetailsPage.deleteMethodConfig()
              val list = await ready new WorkspaceMethodConfigListPage(billingProject, workspaceName)
              list.hasConfig(configName) shouldBe false
            }
          }
        }
      }
    }
  }


  // negative tests

}
