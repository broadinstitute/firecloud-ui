package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.firecloud.config.{AuthToken, Config, Credentials, UserPool}
import java.io.{File, PrintWriter}
import java.util.UUID

import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec, WebBrowserUtil}
import org.broadinstitute.dsde.firecloud.fixture._
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FreeSpec, ParallelTestExecution, ShouldMatchers}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage

class DataSpec extends FreeSpec with WebBrowserSpec
  with UserFixtures with WorkspaceFixtures with ParallelTestExecution
  with ShouldMatchers with WebBrowser with WebBrowserUtil with CleanUp {

  val billingProject = Config.Projects.default
//  val owner: Credentials = UserPool.chooseProjectOwner
//  val reader: Credentials = UserPool.chooseStudent
//  implicit lazy val authToken: AuthToken = AuthToken(owner)

  "import a participants file" in withWebDriver { implicit driver =>
    val owner = UserPool.chooseProjectOwner
    implicit val authToken: AuthToken = AuthToken(owner)
    withWorkspace(billingProject, "TestSpec_FireCloud_import_participants_file_") { workspaceName =>
      withSignIn(owner) { _ =>
        val filename = "src/test/resources/participants.txt"
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.importFile(filename)
        workspaceDataTab.getNumberOfParticipants shouldBe 1
      }
    }
  }

  val configNs = SimpleMethodConfig.configNamespace
  val configName = SimpleMethodConfig.configName
  val methodName: String = MethodData.SimpleMethod.methodName + "_" + UUID.randomUUID().toString
  val methodConfigName: String = SimpleMethodConfig.configName + "_" + UUID.randomUUID().toString

  def makeTempMetadataFile(filePrefix: String, headers: List[String], rows: List[List[String]]): File = {
    val metadataFile = File.createTempFile(filePrefix, "txt")
    val writer = new PrintWriter(metadataFile)
    val rowStrings = rows map { _.mkString(s"\t") }
    val fileContent = s"""entity:${headers.mkString(s"\t")}\n${rowStrings.mkString(s"\n")}"""
    writer.write(fileContent)
    writer.close()
    metadataFile
  }

  def createAndImportMetadataFile(fileName: String, headers: List[String], dataTab: WorkspaceDataPage): Unit = {
    val data = for {
      h <- headers
    }yield {
      if (h == "participant_id") {
        "participant1"
      } else {
        h.takeRight(1)
      }
    }
    val file = makeTempMetadataFile(fileName, headers, List(data))
    dataTab.importFile(file.getAbsolutePath)
  }

  "Writer and reader should see new columns" - {
    "with no defaults or local preferences when analysis run that creates new columns" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = AuthToken(owner)
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_a_simple_workflow", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", TestData.SingleParticipant.participantEntity)
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)

        signIn(owner)
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        val headers1 = List("participant_id")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
        submissionTab.waitUntilSubmissionCompletes()
        if (submissionTab.readWorkflowStatus() != "Succeeded") {
          submissionTab.readStatusMessage() shouldEqual ""
        }
        workspaceDataTab.open
        //there is at least one filter bug - possibly two that was breaking the tests
        //this clear filter fixes the problem. Can be removed when filter bug fixed
        workspaceDataTab.dataTable.clearFilter
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "output")
      }
    }

    "with local preferences but no defaults when analysis run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = AuthToken(owner)
      withWorkspace(billingProject, "DataSpec_launchAnalysis_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
        signIn(owner)
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.dataTable.hideColumn("test1")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.hideColumn("test2")
        workspaceDataTab.signOut()
        signIn(owner)
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
        submissionTab.waitUntilSubmissionCompletes()
        if (submissionTab.readWorkflowStatus() != "Succeeded") {
          submissionTab.readStatusMessage() shouldEqual ""
        }
        workspaceDataTab.open
        workspaceDataTab.dataTable.filter("")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "output")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
      }
    }

    "with defaults but no local preferences when analysis run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = AuthToken(owner)
      withWorkspace(billingProject, "DataSpec_launchAnalysis_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
        signIn(owner)
        val workspaceSummaryTab = new WorkspaceSummaryPage(Config.Projects.default, workspaceName).open
        workspaceSummaryTab.edit{
          workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\"]}}")
        }
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()
        signIn(owner)
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
        submissionTab.waitUntilSubmissionCompletes()
        if (submissionTab.readWorkflowStatus() != "Succeeded") {
          submissionTab.readStatusMessage() shouldEqual ""
        }
        workspaceDataTab.open
        workspaceDataTab.dataTable.filter("")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
      }
    }
    "with defaults and local preferences when analysis is run" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = AuthToken(owner)
      withWorkspace(billingProject, "DataSpec_localDefaults_analysis", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        api.importMetaData(billingProject, workspaceName, "entities", "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")
        api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
          SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
        signIn(owner)
        val workspaceSummaryTab = new WorkspaceSummaryPage(billingProject, workspaceName).open
        workspaceSummaryTab.edit{
          workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test3\"], \"hidden\": [\"test2\"]}}")
        }
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
        workspaceDataTab.dataTable.hideColumn("test1")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
        workspaceDataTab.dataTable.hideColumn("test3")
        workspaceDataTab.signOut()
        signIn(owner)
        val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
        val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
        submissionTab.waitUntilSubmissionCompletes()
        if (submissionTab.readWorkflowStatus() != "Succeeded") {
          submissionTab.readStatusMessage() shouldEqual ""
        }
        workspaceDataTab.open
        workspaceDataTab.dataTable.filter("")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test3", "output")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
      }
    }
  }

  "Writer and reader should see new columns" - {
    "With no defaults or local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = AuthToken(owner)
      withWorkspace(billingProject, "DataSpec_column_display", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(owner)
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        val headers1 = List("participant_id", "test1")
        createAndImportMetadataFile("DataSpec_column_display", headers1, workspaceDataTab)
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
        val headers2 = headers1 :+ "test2"
        createAndImportMetadataFile("DataSpec_column_display2", headers2, workspaceDataTab)
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers2
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers2
      }
    }

    "With local preferences, but no defaults when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = AuthToken(owner)
      withWorkspace(billingProject, "DataSpec_col_display_w_preferences", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(owner)
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        val headers1 = List("participant_id", "test1", "test2")
        createAndImportMetadataFile("DataSpec_column_display", headers1, workspaceDataTab)
        workspaceDataTab.dataTable.hideColumn("test1")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2")
        workspaceDataTab.signOut()

        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual headers1
        workspaceDataTab.dataTable.hideColumn("test2")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()

        signIn(owner)
        workspaceDataTab.open
        val headers2 = List("participant_id", "test1", "test2", "test3")
        createAndImportMetadataFile("DataSpec_column_display2", headers2, workspaceDataTab)
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test2", "test3")
        workspaceDataTab.signOut()

        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
      }
    }

    "With defaults on workspace, but no local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = AuthToken(owner)
      withWorkspace(billingProject, "DataSpec_col_display_w_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) {
        workspaceName =>

          signIn(owner)
          val workspaceSummaryTab = new WorkspaceSummaryPage(billingProject, workspaceName).open
          workspaceSummaryTab.edit{
            workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\", \"test3\"]}}")
          }
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          val headers1 = List("participant_id", "test1", "test2", "test3")
          createAndImportMetadataFile("DataSpec_column_display", headers1, workspaceDataTab)
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          workspaceDataTab.signOut()

          signIn(reader)
          workspaceDataTab.open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
          workspaceDataTab.signOut()

          signIn(owner)
          workspaceDataTab.open
          val headers2 = headers1 :+ "test4"
          createAndImportMetadataFile("DataSpec_column_display2", headers2, workspaceDataTab)
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
          workspaceDataTab.signOut()

          signIn(reader)
          workspaceDataTab.open
          workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
      }
    }

    "With defaults on workspace and local preferences for reader and writer when writer imports metadata with new column" in withWebDriver { implicit driver =>
      val owner = UserPool.chooseProjectOwner
      val reader = UserPool.chooseStudent
      implicit val authToken: AuthToken = AuthToken(owner)
      withWorkspace(billingProject, "DataSpec_col_display_w_defaults_and_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(owner)
        val workspaceSummaryTab = new WorkspaceSummaryPage(billingProject, workspaceName).open
        workspaceSummaryTab.edit{
          workspaceSummaryTab.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test4\"], \"hidden\": [\"test2\", \"test3\"]}}")
        }
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        val headers1 = List("participant_id", "test1", "test2", "test3", "test4")
        createAndImportMetadataFile("DataSpec_column_display", headers1, workspaceDataTab)
        workspaceDataTab.dataTable.hideColumn("test1")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test4")
        workspaceDataTab.signOut()

        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.hideColumn("test4")
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()

        signIn(owner)
        workspaceDataTab.open
        val headers2 = headers1 :+ "test5"
        createAndImportMetadataFile("DataSpec_column_display2", headers2, workspaceDataTab)
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test4", "test5")
        workspaceDataTab.signOut

        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.dataTable.readColumnHeaders shouldEqual List("participant_id", "test1", "test5")
      }
    }
  }
}
