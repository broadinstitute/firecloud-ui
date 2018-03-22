package org.broadinstitute.dsde.firecloud.test.analysis

import org.broadinstitute.dsde.firecloud.fixture.TestData
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{MethodData, SimpleMethodConfig}

class MethodLaunchSpec extends MethodConfigSpecBase {

  "launch a simple workflow" in withWebDriver { implicit driver =>
    val user = Config.Users.owner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_a_simple_workflow") { workspaceName =>
      api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
        SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
      withSignIn(user) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open

        val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId)

        submissionDetailsPage.waitUntilSubmissionCompletes() //This feels like the wrong way to do this?
        submissionDetailsPage.readWorkflowStatus() shouldBe submissionDetailsPage.SUCCESS_STATUS
      }

    }
  }

  "launch modal with no default entities" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "TestSpec_FireCloud_launch_modal_no_default_entities") { workspaceName =>
      api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

      val methodConfigName = workspaceName + "MethodConfig"
      api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
      api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, MethodData.SimpleMethod, billingProject,
        methodConfigName, SimpleMethodConfig.snapshotId, Map.empty, Map.empty, "participant_set")
      withSignIn(user) { _ =>
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, billingProject, methodConfigName).open
        val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
        launchModal.verifyNoRowsMessage() shouldBe true
        launchModal.xOut()
      }
    }
  }

  "launch modal with workflows warning" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
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

  "launch workflow with wrong root entity" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
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
        launchModal.verifyErrorText(wrongRootEntityErrorText)  shouldBe true
        launchModal.xOut()
      }
    }
  }

  "launch workflow on set without expression" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
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

  "launch workflow with input not defined" in withWebDriver { implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
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
