package org.broadinstitute.dsde.firecloud.test.metadata

import java.io.{File, PrintWriter}

import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.WorkspaceFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.{WorkspaceDataPage, WorkspaceSummaryPage}
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, ParallelTestExecution, ShouldMatchers}

class DataSpec extends FreeSpec with WebBrowserSpec with ParallelTestExecution
  with ShouldMatchers with WorkspaceFixtures with CleanUp {

  "A workspace owner should be able to import a participants file" in withWebDriver { implicit driver =>
    implicit val authToken: AuthToken = AuthTokens.harry
    withWorkspace(Config.Projects.default, "DataSpec_import_participants_file") { workspaceName =>
      val filename = "src/test/resources/participants.txt"

      signIn(Config.Users.harry)
      val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
      workspaceDataTab.importFile(filename)
      assert(workspaceDataTab.getNumberOfParticipants() == 1)

      //more checks should be added here
    }
  }

  def makeTempMetadataFile(filePrefix: String, headers: List[String], rows: List[List[String]]): File = {
    val metadataFile = File.createTempFile(filePrefix, "txt")
    val writer = new PrintWriter(metadataFile)
    val rowStrings = rows map { _.mkString(s"\t") }
    val fileContent = s"""entity:${headers.mkString(s"\t")}\n${rowStrings.mkString(s"\n")}"""
    writer.write(fileContent)
    writer.close()
    metadataFile
  }

  "Writer and reader should see new columns" - {
    "With no defaults or local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      implicit val authToken: AuthToken = AuthTokens.harry
      withWorkspace(Config.Projects.default, "DataSpec_column_display", aclEntries = List(AclEntry(Config.Users.ron.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(Config.Users.harry)
        val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        val headers1 = List("participant_id", "test1")
        val metaDataFile1 = makeTempMetadataFile("DataSpec_column_display", headers1, List(List("participant1", "1")))
        workspaceDataTab.importFile(metaDataFile1.getAbsolutePath)
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers1
        val headers2 = headers1 :+ "test2"
        val metaDataFile2 = makeTempMetadataFile("DataSpec_column_display2", headers2, List(List("participant1", "1", "2")))
        workspaceDataTab.importFile(metaDataFile2.getAbsolutePath)
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers2
        workspaceDataTab.signOut()
        signIn(Config.Users.ron)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers2
      }
    }

    "With local preferences, but no defaults when writer imports metadata with new column" in withWebDriver { implicit driver =>
      implicit val authToken: AuthToken = AuthTokens.harry
      withWorkspace(Config.Projects.default, "DataSpec_col_display_w_preferences", aclEntries = List(AclEntry(Config.Users.ron.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(Config.Users.harry)
        val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        val headers1 = List("participant_id", "test1", "test2")
        val metaDataFile1 = makeTempMetadataFile("DataSpec_column_display", headers1, List(List("participant1", "1", "2")))
        workspaceDataTab.importFile(metaDataFile1.getAbsolutePath)
        workspaceDataTab.hideColumn("test1")
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test2")
        workspaceDataTab.signOut()

        signIn(Config.Users.ron)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual headers1
        workspaceDataTab.hideColumn("test2")
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()

        signIn(Config.Users.harry)
        workspaceDataTab.open
        val headers2 = List("participant_id", "test1", "test2", "test3")
        val metaDataFile2 = makeTempMetadataFile("DataSpec_column_display2", headers2, List(List("participant1", "1", "2", "3")))
        workspaceDataTab.importFile(metaDataFile2.getAbsolutePath)
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test2", "test3")
        workspaceDataTab.signOut()

        signIn(Config.Users.ron)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test3")
      }
    }

    "With defaults on workspace, but no local preferences when writer imports metadata with new column" in withWebDriver { implicit driver =>
      implicit val authToken: AuthToken = AuthTokens.harry
      withWorkspace(Config.Projects.default, "DataSpec_col_display_w_defaults", aclEntries = List(AclEntry(Config.Users.ron.email, WorkspaceAccessLevel.Reader))) {
        workspaceName =>

          signIn(Config.Users.harry)
          val workspaceSummaryTab = new WorkspaceSummaryPage(Config.Projects.default, workspaceName).open
          workspaceSummaryTab.ui.beginEditing
          workspaceSummaryTab.ui.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\"], \"hidden\": [\"test2\", \"test3\"]}}")
          workspaceSummaryTab.ui.save
          val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
          val headers1 = List("participant_id", "test1", "test2", "test3")
          val metaDataFile1 = makeTempMetadataFile("DataSpec_column_display", headers1, List(List("participant1", "1", "2", "3")))
          workspaceDataTab.importFile(metaDataFile1.getAbsolutePath)
          workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
          workspaceDataTab.signOut()

          signIn(Config.Users.ron)
          workspaceDataTab.open
          workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
          workspaceDataTab.signOut()

          signIn(Config.Users.harry)
          workspaceDataTab.open
          val headers2 = headers1 :+ "test4"
          val metaDataFile2 = makeTempMetadataFile("DataSpec_column_display", headers2, List(List("participant1", "1", "2", "3", "4")))
          workspaceDataTab.importFile(metaDataFile2.getAbsolutePath)
          workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
          workspaceDataTab.signOut()

          signIn(Config.Users.ron)
          workspaceDataTab.open
          workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test4")
      }
    }

    "With defaults on workspace and local preferences for reader and writer when writer imports metadata with new column" in withWebDriver { implicit driver =>
      implicit val authToken: AuthToken = AuthTokens.harry
      withWorkspace(Config.Projects.default, "DataSpec_col_display_w_defaults_and_local", aclEntries = List(AclEntry(Config.Users.ron.email, WorkspaceAccessLevel.Reader))) { workspaceName =>

        signIn(Config.Users.harry)
        val workspaceSummaryTab = new WorkspaceSummaryPage(Config.Projects.default, workspaceName).open
        workspaceSummaryTab.ui.beginEditing
        workspaceSummaryTab.ui.addWorkspaceAttribute("workspace-column-defaults", "{\"participant\": {\"shown\": [\"participant_id\", \"test1\" \"test4\"], \"hidden\": [\"test2\", \"test3\"]}}")
        workspaceSummaryTab.ui.save
        val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
        val headers1 = List("participant_id", "test1", "test4", "test2", "test3")
        val metaDataFile1 = makeTempMetadataFile("DataSpec_column_display", headers1, List(List("participant1", "1", "4", "2", "3")))
        workspaceDataTab.importFile(metaDataFile1.getAbsolutePath)
        workspaceDataTab.hideColumn("test1")
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test4")
        workspaceDataTab.signOut()

        signIn(Config.Users.ron)
        workspaceDataTab.open
        workspaceDataTab.hideColumn("test4")
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1")
        workspaceDataTab.signOut()

        signIn(Config.Users.harry)
        workspaceDataTab.open
        val headers2 = headers1 :+ "test5"
        val metaDataFile2 = makeTempMetadataFile("DataSpec_column_display1", headers2, List(List("participant1", "1", "4", "2", "3")))
        workspaceDataTab.importFile(metaDataFile2.getAbsolutePath)
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test4", "test5")
        workspaceDataTab.signOut

        signIn(Config.Users.ron)
        workspaceDataTab.open
        workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test1", "test5")
      }
    }
  }
}
