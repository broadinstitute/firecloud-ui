package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.workbench.config.{Config, UserPool}
import java.io.{File, PrintWriter}
import java.util.UUID

import org.broadinstitute.dsde.firecloud.fixture.{TestData, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.fixture._
import org.broadinstitute.dsde.workbench.service.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec, WebBrowserUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FreeSpec, Matchers}

import scala.io.Source

class DataSpec extends FreeSpec with WebBrowserSpec with UserFixtures with WorkspaceFixtures
  with Matchers with WebBrowser with WebBrowserUtil {


  private val billingProject = Config.Projects.default
  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString


  "import a participants file" in withWebDriver { implicit driver =>
    val owner = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = owner.makeAuthToken()
    withWorkspace(billingProject, "TestSpec_FireCloud_import_participants_file_") { workspaceName =>
      withSignIn(owner) { _ =>
        val filename = "src/test/resources/participants.txt"
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.importFile(filename)
        workspaceDataTab.getNumberOfParticipants shouldBe 1
      }
    }
  }

  "Writer and reader should see new columns" - {
    "with no defaults or local preferences when analysis run that creates new columns" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_a_simple_workflow", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withSignIn(owner) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          val headers1 = List("participant_id")
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
          val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
          submissionTab.waitUntilSubmissionCompletes()
          assert(submissionTab.verifyWorkflowSucceeded())
          workspaceDataTab.open
          //there is at least one filter bug - possibly two that was breaking the tests
          //1) Not sure if bug or not: filter from launch analysis modal is still present when data tab revisited
          //2) Filter on the datatab removes even the row being referenced
          //this clear filter fixes the problem. Can be removed when filter bug fixed
          workspaceDataTab.dataTable.clearFilter()
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output")
        }
        withSignIn(reader) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output")
        }
      }
    }

    "with local preferences but no defaults when analysis run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withWorkspace(billingProject, "DataSpec_launchAnalysis_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

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
          val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
          submissionTab.waitUntilSubmissionCompletes()
          assert(submissionTab.verifyWorkflowSucceeded())
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.clearFilter()
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "output")
        }
        withSignIn(reader) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
        }
      }
    }

    "with defaults but no local preferences when analysis run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withWorkspace(billingProject, "DataSpec_launchAnalysis_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withSignIn(owner) { _ =>
          val workspaceSummaryTab = new WorkspaceSummaryPage(Config.Projects.default, workspaceName).open
          workspaceSummaryTab.edit {
            workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\"]}}")
          }
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
        }
        withSignIn(reader) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
        }
        withSignIn(owner) { _ =>
          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
          val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
          submissionTab.waitUntilSubmissionCompletes()
          assert(submissionTab.verifyWorkflowSucceeded())
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.clearFilter()
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
        }
        withSignIn(reader) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
        }
      }
    }

    "with defaults and local preferences when analysis is run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = owner.makeAuthToken()
      withWorkspace(billingProject, "DataSpec_localDefaults_analysis", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        withSignIn(owner) { _ =>
          val workspaceSummaryTab = new WorkspaceSummaryPage(billingProject, workspaceName).open
          workspaceSummaryTab.edit {
            workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test3\"], \"hidden\": [\"test2\"]}}")
          }
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
          workspaceDataTab.dataTable.hideColumn("test1")
        }
        withSignIn(reader) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
          workspaceDataTab.dataTable.hideColumn("test3")
        }
        withSignIn(owner) { _ =>
          api.workspaces.waitForBucketReadAccess(billingProject, workspaceName)
          val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
          val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
          submissionTab.waitUntilSubmissionCompletes()
          assert(submissionTab.verifyWorkflowSucceeded())
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.clearFilter()
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test3", "output")
        }
        withSignIn(reader) { _ =>
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
        }
      }
    }
  }

  //  This test is just to make sure functionality in this context works
  //  BUT we should really also write some tests for this specific component (seperate of this context)
  "Column reordering should be reflected" in withWebDriver {implicit driver =>
    val user = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = user.makeAuthToken()
    withWorkspace(billingProject, "DataSpec_reordercolumns") {workspaceName =>
      api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")
      withSignIn(user) {_ =>
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.dataTable.moveColumn("test1", "test3")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "test3", "test1")
      }
    }
  }

}
