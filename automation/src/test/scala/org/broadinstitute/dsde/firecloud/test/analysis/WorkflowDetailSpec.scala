package org.broadinstitute.dsde.firecloud.test.analysis

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.SubmissionDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.UserPool
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.{Orchestration, Rawls}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest._


class WorkflowDetailSpec extends FreeSpec with Matchers with WebBrowserSpec
  with WorkspaceFixtures with BillingFixtures with UserFixtures with SubWorkflowFixtures {

  "WorkflowDetailSpec" - {
    "should retrieve subworkflow details" in withWebDriver { implicit webDriver =>
      val student = UserPool.chooseStudent
      implicit val token: AuthToken = student.makeAuthToken()

      // this will run scatterCount^levels workflows, so be careful if increasing these values!
      val topLevelMethod: Method = methodTree(levels = 3, scatterCount = 3)

      withCleanBillingProject(student) { projectName =>
        withWorkspace(projectName, "rawls-subworkflow-metadata") { workspaceName =>
          Orchestration.methodConfigurations.createMethodConfigInWorkspace(
            projectName, workspaceName,
            topLevelMethod,
            topLevelMethodConfiguration.configNamespace, topLevelMethodConfiguration.configName, topLevelMethodConfiguration.snapshotId,
            topLevelMethodConfiguration.inputs(topLevelMethod), topLevelMethodConfiguration.outputs(topLevelMethod), topLevelMethodConfiguration.rootEntityType)

          Orchestration.importMetaData(projectName, workspaceName, "entities", SingleParticipant.participantEntity)

          // it currently takes ~ 5 min for google bucket read permissions to propagate.
          // We can't launch a workflow until this happens.
          // See https://github.com/broadinstitute/workbench-libs/pull/61 and https://broadinstitute.atlassian.net/browse/GAWB-3327

          Orchestration.workspaces.waitForBucketReadAccess(projectName, workspaceName)

          val submissionId = Rawls.submissions.launchWorkflow(
            projectName, workspaceName,
            topLevelMethodConfiguration.configNamespace, topLevelMethodConfiguration.configName,
            "participant", SingleParticipant.entityId, "this", useCallCache = false)

          withSignIn(student) { _ =>
            val monitorPage = new SubmissionDetailsPage(projectName, workspaceName, submissionId).open
            monitorPage.awaitReady()

            monitorPage.getSubmissionId shouldBe submissionId
          }

          // clean up: Abort and wait for Aborted

          Rawls.submissions.abortSubmission(projectName, workspaceName, submissionId)

          eventually {
            val (status, _) = Rawls.submissions.getSubmissionStatus(projectName, workspaceName, submissionId)

            withClue(s"Submission $projectName/$workspaceName/$submissionId: ") {
              status shouldBe "Aborted"
            }
          }

        }
      }
    }
  }
}
