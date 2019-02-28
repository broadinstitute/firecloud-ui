package org.broadinstitute.dsde.firecloud.test.analysis

import java.util.UUID

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.SubmissionDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._
import org.scalatest.time.{Minutes, Seconds, Span}


class MethodLaunchSpec extends FreeSpec with ParallelTestExecution with Matchers with WebBrowserSpec with WorkspaceFixtures
  with UserFixtures with MethodFixtures with BillingFixtures with TestReporterFixture {

  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString
  val wrongRootEntityErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type participant."
  val noExpressionErrorText: String = "Error: Method configuration expects an entity of type sample, but you gave us an entity of type sample_set."
  val missingInputsErrorText: String = "Missing inputs:"
  val testData = TestData()


  "launch workflow with warning" in {
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "MethodLaunchSpec_launch_modal_with_workflows_warning") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
        api.importMetaData(billingProject, workspaceName, "entities", testData.participantEntity)
        api.importMetaData(billingProject, workspaceName, "entities", testData.hundredAndOneSet.samples)
        api.importMetaData(billingProject, workspaceName, "entities", testData.hundredAndOneSet.sampleSetCreation)
        api.importMetaData(billingProject, workspaceName, "entities", testData.hundredAndOneSet.sampleSetMembership)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withWebDriver { implicit driver =>
          withSignIn(user) { _ =>
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

            val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
            launchModal.filterRootEntityType("sample_set")
            launchModal.searchAndSelectEntity(testData.hundredAndOneSet.sampleSetId)
            launchModal.verifyWorkflowsWarning() shouldBe true
            launchModal.xOut()
          }
        }
      }
    }
  }

  "launch workflow with wrong root entity" in {
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "MethodLaunchSpec_launch_workflow_with_wrong_root_entity") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
        api.importMetaData(billingProject, workspaceName, "entities", testData.participantEntity)
        api.importMetaData(billingProject, workspaceName, "entities", testData.hundredAndOneSet.samples)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withWebDriver { implicit driver =>
          withSignIn(user) { _ =>
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))

            val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
            launchModal.filterRootEntityType("participant")
            launchModal.searchAndSelectEntity(testData.participantId)
            launchModal.clickLaunchButton()
            launchModal.verifyErrorText(wrongRootEntityErrorText) shouldBe true
            launchModal.xOut()
          }
        }
      }
    }
  }

  "launch workflow on set without expression" in {
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "MethodLaunchSpec_launch_workflow_on_set_without_expression") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
        api.importMetaData(billingProject, workspaceName, "entities", testData.participantEntity)
        api.importMetaData(billingProject, workspaceName, "entities", testData.hundredAndOneSet.samples)
        api.importMetaData(billingProject, workspaceName, "entities", testData.hundredAndOneSet.sampleSetCreation)
        api.importMetaData(billingProject, workspaceName, "entities", testData.hundredAndOneSet.sampleSetMembership)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withWebDriver { implicit driver =>
          withSignIn(user) { _ =>
            val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
            methodConfigDetailsPage.editMethodConfig(newRootEntityType = Some("sample"))
            val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
            launchModal.filterRootEntityType("sample_set")
            launchModal.searchAndSelectEntity(testData.hundredAndOneSet.sampleSetId)
            launchModal.clickLaunchButton()
            launchModal.verifyErrorText(noExpressionErrorText) shouldBe true
            launchModal.xOut()
          }
        }
      }
    }
  }

  "launch workflow with input not defined" in {
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "MethodLaunchSpec_launch_workflow_input_not_defined") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
        api.importMetaData(billingProject, workspaceName, "entities", testData.participantEntity)
        withMethod("MethodLaunchSpec_input_undefined", MethodData.InputRequiredMethod, 1) { methodName =>
          val method = MethodData.InputRequiredMethod.copy(methodName = methodName)
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName, method,
            method.methodNamespace, methodConfigName, 1, Map.empty, Map.empty, method.rootEntityType)

          withWebDriver { implicit driver =>
            withSignIn(user) { _ =>
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, method.methodNamespace, methodConfigName).open
              val launchModal = methodConfigDetailsPage.openLaunchAnalysisModal()
              launchModal.filterRootEntityType(method.rootEntityType)
              launchModal.searchAndSelectEntity(testData.participantId)
              launchModal.clickLaunchButton()
              launchModal.verifyErrorText(missingInputsErrorText) shouldBe true
              launchModal.xOut()
            }
          }
        }
      }
    }
  }

  "launch a method from the method repo" in {
    val user = FireCloudConfig.Users.owner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "MethodLaunchSpec_launch_method_from_workspace") { workspaceName =>
        withMethod("MethodLaunchSpec_methodrepo", MethodData.SimpleMethod, 1) { methodName =>
          val method = MethodData.SimpleMethod.copy(methodName = methodName)
          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          api.importMetaData(billingProject, workspaceName, "entities", testData.participantEntity)
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName,
            method, SimpleMethodConfig.configNamespace, methodConfigName, 1,
            SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, MethodData.SimpleMethod.rootEntityType)

          withWebDriver { implicit driver =>
            withSignIn(user) { _ =>
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
              val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, testData.participantId)
              val submissionId = submissionDetailsPage.getSubmissionId

              implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Minutes)), interval = scaled(Span(20, Seconds)))

              // test will end when api status becomes Running. not waiting for Done
              eventually {
                val status = submissionDetailsPage.getApiSubmissionStatus(billingProject, workspaceName, submissionId)
                logger.info(s"Status is $status in Submission $billingProject/$workspaceName/$submissionId")
                withClue(s"Monitoring Submission $billingProject/$workspaceName/$submissionId. Status is Done or Submitted.") {
                  status should (equal("Done") or equal ("Submitted"))
                }
              }
            }
          }
        }
      }
    }
  }

  "owner can abort a launched submission" in {
    val user = FireCloudConfig.Users.owner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withCleanBillingProject(user) { billingProject =>
      withWorkspace(billingProject, "MethodLaunchSpec_abort_submission") { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)

        val shouldUseCallCaching = false
        api.importMetaData(billingProject, workspaceName, "entities", testData.participantEntity)

        withMethod("MethodLaunchSpec_abort", MethodData.SimpleMethod) { methodName =>
          val method = MethodData.SimpleMethod.copy(methodName = methodName)
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName,
            method, SimpleMethodConfig.configNamespace, methodName, 1,
            SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, MethodData.SimpleMethod.rootEntityType)

          withWebDriver { implicit driver =>
            withSignIn(user) { _ =>
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodName).open
              val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, testData.participantId, "", shouldUseCallCaching)
              val submissionId = submissionDetailsPage.getSubmissionId

              submissionDetailsPage.abortSubmission()

              implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Minutes)), interval = scaled(Span(20, Seconds)))

              // wait api status becomes Aborted
              eventually {
                val status = submissionDetailsPage.getApiSubmissionStatus(billingProject, workspaceName, submissionId)
                logger.info(s"Status is $status in Submission $billingProject/$workspaceName/$submissionId")
                withClue(s"Monitoring Submission $billingProject/$workspaceName/$submissionId. Waited for status Aborted.") {
                  status shouldBe "Aborted"
                }
              }

              // verifiy on UI
              submissionDetailsPage.waitUntilSubmissionCompletes()
              submissionDetailsPage.getSubmissionStatus shouldBe submissionDetailsPage.ABORTED_STATUS

              // once aborted, the abort button should no longer be visible
              submissionDetailsPage should not be 'abortButtonVisible

            }
          }
        }
      }
    }
  }

  "reader does not see an abort button for a launched submission" in {
    val owner = FireCloudConfig.Users.owner
    val reader = UserPool.chooseStudent
    implicit val ownerAuthToken: AuthToken = owner.makeAuthToken()
    val readerAuthToken: AuthToken = reader.makeAuthToken()

    withCleanBillingProject(owner) { billingProject =>
      withWorkspace(billingProject, "MethodLaunchSpec_reader_cannot_abort_submission", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)(ownerAuthToken)
        api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)(readerAuthToken)

        val shouldUseCallCaching = false
        api.importMetaData(billingProject, workspaceName, "entities", testData.participantEntity)

        withMethod("MethodLaunchSpec_abort_reader", MethodData.SimpleMethod) { methodName =>
          val method = MethodData.SimpleMethod.copy(methodName = methodName)
          api.methodConfigurations.createMethodConfigInWorkspace(billingProject, workspaceName,
            method, SimpleMethodConfig.configNamespace, methodName, 1,
            SimpleMethodConfig.inputs, SimpleMethodConfig.outputs, MethodData.SimpleMethod.rootEntityType)

          withWebDriver { implicit driver =>

            // TODO: avoid using var?
            var submissionId: String = ""

            // as owner, launch a submission
            withSignIn(owner) { _ =>
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodName).open
              val submissionDetailsPage = methodConfigDetailsPage.launchAnalysis(MethodData.SimpleMethod.rootEntityType, testData.participantId, "", shouldUseCallCaching)
              submissionId = submissionDetailsPage.getSubmissionId
            }

            // as reader, view submission details and validate the abort button doesn't appear
            withSignIn(reader) { _ =>
              withClue("submissionId as returned by owner block: ") {
                submissionId should not be ""
              }
              val submissionDetailsPage = new SubmissionDetailsPage(billingProject, workspaceName, submissionId).open
              val status = submissionDetailsPage.getApiSubmissionStatus(billingProject, workspaceName, submissionId)(readerAuthToken)
              withClue("When the reader views the owner's submission, the submission status: ") {
                // the UI shows the abort button for the following statuses:
                List("Accepted", "Evaluating", "Submitting", "Submitted") should contain (status)
              }

              // test the page's display of the submission ID against the tests' knowledge of the ID. This verifies
              // we have landed on the right page. Without this test, the next assertion on the abort button's visibility
              // is a weak assertion - we could be on the wrong page, which means the abort button would also not be visible.
              submissionDetailsPage.getSubmissionId shouldBe submissionId

              // is the abort button visible?
              submissionDetailsPage should not be 'abortButtonVisible
            }

          }
        }
      }
    }
  }

}
