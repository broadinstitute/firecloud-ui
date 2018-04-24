package org.broadinstitute.dsde.firecloud.test.analysis

import java.util.UUID

import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.{WorkspaceMethodConfigDetailsPage}
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._


class MethodConfigSpec extends FreeSpec with Matchers with WebBrowserSpec with WorkspaceFixtures with UserFixtures with MethodFixtures with BillingFixtures {

  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString
  val wrongRootEntityErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type participant."
  val noExpressionErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type sample_set."
  val missingInputsErrorText: String = "is missing definitions for these inputs:"

  "launch workflow and delete a workflow" in withWebDriver { implicit driver =>
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

  "launch workflow with warning" in withWebDriver { implicit driver =>
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
      withWorkspace(billingProject, "MethodConfigSpec_launch_workflow_input_not_defined") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)

        val method = MethodData.InputRequiredMethod
        withMethod("MethodConfigSpec_input_undefined", method, 1) { methodName =>
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, method.copy(methodName = methodName),
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
  }

  "launch a method from the method repo" in withWebDriver { implicit driver =>
    val user = Config.Users.owner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_method_from_workspace") { workspaceName =>
        withMethod("MethodConfigSpec_methodrepo", MethodData.SimpleMethod, 1) { methodName =>
          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName,
            MethodData.SimpleMethod.copy(methodName = methodName), SimpleMethodConfig.configNamespace, methodConfigName, 1,
            SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, MethodData.SimpleMethod.rootEntityType)

          withSignIn(user) { _ =>
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, TestData.SingleParticipant.entityId)

            submissionDetailsPage.waitUntilSubmissionCompletes()
            submissionDetailsPage.verifyWorkflowSucceeded() shouldBe true
          }
        }
      }
    }
  }

}
