package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.firecloud.config.{AuthToken, Config, UserPool, Credentials}
import java.io.{File, PrintWriter}
import java.util.UUID

import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec, WebBrowserUtil}
import org.broadinstitute.dsde.firecloud.fixture.{UserFixtures, WorkspaceFixtures}
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FlatSpec, ParallelTestExecution, ShouldMatchers}
import org.broadinstitute.dsde.firecloud.fixture.MethodData.SimpleMethod
import org.broadinstitute.dsde.firecloud.fixture.{SimpleMethodConfig, _}
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.{SubmissionDetailsPage, WorkspaceMonitorPage}
import org.broadinstitute.dsde.firecloud.page.workspaces.{WorkspaceDataPage, WorkspaceSummaryPage}

class DataSpec extends FlatSpec with WebBrowserSpec
  with UserFixtures with WorkspaceFixtures with ParallelTestExecution
  with ShouldMatchers with WebBrowser with WebBrowserUtil with CleanUp {

  val billingProject = Config.Projects.default
  val defaultUser: Credentials = UserPool.chooseStudent
  implicit val authToken: AuthToken = AuthToken(defaultUser)
  it should "import a participants file" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_import_participants_file_") { workspaceName =>
      withSignIn(defaultUser) { _ =>
        val filename = "src/test/resources/participants.txt"
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.importFile(filename)
        workspaceDataTab.getNumberOfParticipants shouldBe 1
      }
    }
  }
  implicit lazy val authToken: AuthToken = AuthTokens.hermione
  val owner = Config.Users.hermione
  val reader = Config.Users.draco

    }
  }

  object SimpleMethodConfig {
    val configName = "DO_NOT_CHANGE_test1_config"
    val configNamespace = "automationmethods"
    val snapshotId = 1
    val rootEntityType = "participant"
    val inputs = Map("test.hello.name" -> "\"a\"") // shouldn't be needed for config
    val outputs = Map("test.hello.response" -> "workspace.result", "test.hello.name" -> "participant.name")
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

  def setupWithApi(workspaceName: String, fileString: String): Unit = {
    api.importMetaData(billingProject, workspaceName, "entities", fileString)
    api.methodConfigurations.copyMethodConfigFromMethodRepo(billingProject, workspaceName, SimpleMethodConfig.configNamespace,
      SimpleMethodConfig.configName, SimpleMethodConfig.snapshotId, SimpleMethodConfig.configNamespace, methodConfigName)
  }

  def launch(workspaceName: String): Unit = {
    signIn(owner)
    val methodConfigDetailsPage = new WorkspaceMethodConfigDetailsPage(billingProject, workspaceName, SimpleMethodConfig.configNamespace, methodConfigName).open
    val submissionTab = methodConfigDetailsPage.launchAnalysis(SimpleMethodConfig.rootEntityType, TestData.SingleParticipant.entityId, "", false)
    submissionTab.waitUntilSubmissionCompletes()
  }
  
  "Writer and reader should see new columns" - {
    "with no defaults or local preferences when analysis run that creates new columns" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "TestSpec_FireCloud_launch_a_simple_workflow", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        setupWithApi(workspaceName, TestData.SingleParticipant.participantEntity)

        signIn(owner)
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        val headers1 = List("participant_id")
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers1
        launch(workspaceName)
        workspaceDataTab.open
        workspaceDataTab.ui.clearFilterField()
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "output")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "output")
      }
    }

    "with local preferences but no defaults when analysis run" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "DataSpec_launchAnalysis_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        setupWithApi(workspaceName, "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
        signIn(owner)
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.hideColumn("test1")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.hideColumn("test2")
        workspaceDataTab.signOut()
        signIn(owner)
        launch(workspaceName)
        workspaceDataTab.open
        workspaceDataTab.ui.clearFilterField()
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test2", "output")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
      }
    }

    "with defaults but no local preferences when analysis run" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "DataSpec_launchAnalysis_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        setupWithApi(workspaceName, "entity:participant_id\ttest1\ttest2\nparticipant1\t1\t2")
        signIn(owner)
        val workspaceSummaryTab = new WorkspaceSummaryPage(Config.Projects.default, workspaceName).open
        workspaceSummaryTab.ui.beginEditing
        workspaceSummaryTab.ui.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\"]}}")
        workspaceSummaryTab.ui.save
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()
        launch(workspaceName)
        workspaceDataTab.open
        workspaceDataTab.ui.clearFilterField()
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
      }
    }
    "with defaults and local preferences when analysis is run" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "DataSpec_localDefaults_analysis", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>
        setupWithApi(workspaceName, "entity:participant_id\ttest1\ttest2\ttest3\nparticipant1\t1\t2\t3")
        signIn(owner)
        val workspaceSummaryTab = new WorkspaceSummaryPage(Config.Projects.default, workspaceName).open
        workspaceSummaryTab.ui.beginEditing
        workspaceSummaryTab.ui.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\", \"test3\"], \"hidden\": [\"test2\"]}}")
        workspaceSummaryTab.ui.save
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
        workspaceDataTab.hideColumn("test1")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
        workspaceDataTab.hideColumn("test3")
        workspaceDataTab.signOut()
        signIn(owner)
        launch(workspaceName)
        workspaceDataTab.open
        workspaceDataTab.ui.clearFilterField()
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test3", "output")
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "output")
      }
    }
  }

  "Writer and reader should see new columns" - {
    "With no defaults or local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "DataSpec_column_display", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(owner)
        val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        val headers1 = List("participant_id", "test1")
        createAndImportMetadataFile("DataSpec_column_display", headers1, workspaceDataTab)
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers1
        val headers2 = headers1 :+ "test2"
        createAndImportMetadataFile("DataSpec_column_display2", headers2, workspaceDataTab)
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers2
        workspaceDataTab.signOut()
        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers2
      }
    }

    "With local preferences, but no defaults when writer imports metadata with new column" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "DataSpec_col_display_w_preferences", aclEntries = List(AclEntry(Config.Users.draco.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(owner)
        val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        val headers1 = List("participant_id", "test1", "test2")
        createAndImportMetadataFile("DataSpec_column_display", headers1, workspaceDataTab)
        workspaceDataTab.hideColumn("test1")
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test2")
        workspaceDataTab.signOut()

        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers1
        workspaceDataTab.hideColumn("test2")
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()

        signIn(owner)
        workspaceDataTab.open
        val headers2 = List("participant_id", "test1", "test2", "test3")
        createAndImportMetadataFile("DataSpec_column_display2", headers2, workspaceDataTab)
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test2", "test3")
        workspaceDataTab.signOut()

        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
      }
    }

    "With defaults on workspace, but no local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "DataSpec_col_display_w_defaults", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) {
        workspaceName =>

          signIn(owner)
          val workspaceSummaryTab = new WorkspaceSummaryPage(billingProject, workspaceName).open
          workspaceSummaryTab.ui.beginEditing
          workspaceSummaryTab.ui.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\", \"test3\"]}}")
          workspaceSummaryTab.ui.save
          val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
          val headers1 = List("participant_id", "test1", "test2", "test3")
          createAndImportMetadataFile("DataSpec_column_display", headers1, workspaceDataTab)
          workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
          workspaceDataTab.signOut()

          signIn(reader)
          workspaceDataTab.open
          workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
          workspaceDataTab.signOut()

          signIn(owner)
          workspaceDataTab.open
          val headers2 = headers1 :+ "test4"
          createAndImportMetadataFile("DataSpec_column_display2", headers2, workspaceDataTab)
          workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
          workspaceDataTab.signOut()

          signIn(reader)
          workspaceDataTab.open
          workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
      }
    }

    "With defaults on workspace and local preferences for reader and writer when writer imports metadata with new column" in withWebDriver { implicit driver =>
      withWorkspace(billingProject, "DataSpec_col_display_w_defaults_and_local", aclEntries = List(AclEntry(reader.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(owner)
        val workspaceSummaryTab = new WorkspaceSummaryPage(Config.Projects.default, workspaceName).open
        workspaceSummaryTab.ui.beginEditing
        workspaceSummaryTab.ui.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\" \"test4\"], \"hidden\": [\"test2\", \"test3\"]}}")
        workspaceSummaryTab.ui.save
        val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        val headers1 = List("participant_id", "test1", "test2", "test3", "test4")
        createAndImportMetadataFile("DataSpec_column_display", headers1, workspaceDataTab)
        workspaceDataTab.hideColumn("test1")
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test2", "test3", "test4")
        workspaceDataTab.signOut()

        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.hideColumn("test4")
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test2", "test3")
        workspaceDataTab.signOut()

        signIn(owner)
        workspaceDataTab.open
        val headers2 = headers1 :+ "test5"
        createAndImportMetadataFile("DataSpec_column_display2", headers2, workspaceDataTab)
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test2", "test3", "test4", "test5")
        workspaceDataTab.signOut

        signIn(reader)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test2", "test3", "test5")
      }
    }
  }
}
