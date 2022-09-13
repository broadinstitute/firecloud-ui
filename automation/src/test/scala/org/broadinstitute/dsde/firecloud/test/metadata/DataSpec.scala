package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.workbench.config.UserPool

import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FreeSpec, Matchers, ParallelTestExecution}


class DataSpec extends FreeSpec with ParallelTestExecution with WebBrowserSpec with UserFixtures with WorkspaceFixtures
  with BillingFixtures with Matchers with TestReporterFixture {

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))

  val methodConfigName: String = randomIdWithPrefix(SimpleMethodConfig.configName)
  val testData = TestData()


  "Writer and reader should see new columns" - {
    "with no defaults or local preferences when analysis run that creates new columns" in {
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_launch_workflow", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          api.importMetaData(billingProject, workspaceName, "entities", testData.participantEntity)
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

          withWebDriver { implicit driver =>
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              val headers1 = List("participant_id")
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1 }
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
              val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, testData.participantId, "", true)
              submissionTab.waitUntilSubmissionCompletes()
              assert(submissionTab.verifyWorkflowSucceeded())
              workspaceDataTab.open
              //there is at least one filter bug - possibly two that was breaking the tests
              //1) Not sure if bug or not: filter from launch analysis modal is still present when data tab revisited
              //2) Filter on the datatab removes even the row being referenced
              //this clear filter fixes the problem. Can be removed when filter bug fixed
              workspaceDataTab.dataTable.clearFilter()
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output") }
            }
          }
        }
      }
    }

    "with local preferences but no defaults when analysis run" in {
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_launchAnalysis_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          api.importMetaData(billingProject, workspaceName, "entities", s"entity:participant_id\ttest1\ttest2\n${testData.participantId}\t1\t2")
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

          withWebDriver { implicit driver =>
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              workspaceDataTab.dataTable.hideColumn("test1")
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              workspaceDataTab.dataTable.hideColumn("test2")
            }
            withSignIn(owner) { _ =>
              api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
              val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, testData.participantId, "", true)
              submissionTab.waitUntilSubmissionCompletes()
              assert(submissionTab.verifyWorkflowSucceeded())
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              workspaceDataTab.dataTable.clearFilter()
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "output") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually {workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output") }
            }
          }
        }
      }
    }

    "with defaults but no local preferences when analysis run" in {
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_launchAnalysis_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          api.importMetaData(billingProject, workspaceName, "entities", s"entity:participant_id\ttest1\ttest2\n${testData.participantId}\t1\t2")
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

          api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\"]}}"))

          withWebDriver { implicit driver =>
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1") }
            }
            withSignIn(owner) { _ =>
              api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
              val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, testData.participantId, "", true)
              submissionTab.waitUntilSubmissionCompletes()
              assert(submissionTab.verifyWorkflowSucceeded())
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              workspaceDataTab.dataTable.clearFilter()
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output") }
            }
          }
        }
      }
    }

    "with defaults and local preferences when analysis is run" in {
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withCleanBillingProject(owner) { billingProject =>
        withWorkspace(billingProject, "DataSpec_localDefaults_analysis", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          api.importMetaData(billingProject, workspaceName, "entities", s"entity:participant_id\ttest1\ttest2\ttest3\n${testData.participantId}\t1\t2\t3")
          api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
            SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
          api.workspaces.setAttributes(billingProject, workspaceName, Map("workspace-column-defaults" -> "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test3\"], \"hidden\": [\"test2\"]}}"))

          withWebDriver { implicit driver =>
            withSignIn(owner) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3") }
              workspaceDataTab.dataTable.hideColumn("test1")
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3") }
              workspaceDataTab.dataTable.hideColumn("test3")
            }
            withSignIn(owner) { _ =>
              api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
              val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
              val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, testData.participantId, "", true)
              submissionTab.waitUntilSubmissionCompletes()
              assert(submissionTab.verifyWorkflowSucceeded())
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              workspaceDataTab.dataTable.clearFilter()
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test3", "output") }
            }
            withSignIn(reader) { _ =>
              val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
              eventually { workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output") }
            }
          }
        }
      }
    }
  }

}
