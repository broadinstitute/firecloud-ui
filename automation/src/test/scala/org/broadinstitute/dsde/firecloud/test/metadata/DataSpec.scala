package org.broadinstitute.dsde.firecloud.test.metadata

import java.io.{File, PrintWriter}

import org.broadinstitute.dsde.firecloud.api.{AclEntry, WorkspaceAccessLevel}
import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.fixture.WorkspaceFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
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

  "A user's column display preferences should persist across sessions" in withWebDriver { implicit driver =>
    implicit val authToken: AuthToken = AuthTokens.ron
    withWorkspace(Config.Projects.default, "DataSpec_column_display_prefs",
      aclEntries = List(AclEntry(Config.Users.harry.email, WorkspaceAccessLevel.Writer))) { workspaceName =>

      signIn(Config.Users.ron)
      val workspaceDataTab = new WorkspaceDataPage(Config.Projects.default, workspaceName).open
      val headers1 = List("participant_id", "test1")
      val metadataFile1 = makeTempMetadataFile("DataSpec_column_display_prefs",
        headers1, List(List("participant1", "1")))
      workspaceDataTab.importFile(metadataFile1.getAbsolutePath)
      workspaceDataTab.ui.readColumnHeaders shouldEqual headers1
      workspaceDataTab.hideColumn("test1")
      workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id")
      workspaceDataTab.signOut()

      signIn(Config.Users.harry)
      workspaceDataTab.open
      workspaceDataTab.ui.readColumnHeaders shouldEqual headers1
      val headers2 = List("participant_id", "test1", "test2")
      val metadataFile2 = makeTempMetadataFile("DataSpec_column_display_prefs",
        headers2, List(List("participant1", "1", "2")))
      workspaceDataTab.importFile(metadataFile2.getAbsolutePath)
      workspaceDataTab.ui.readColumnHeaders shouldEqual headers2
      workspaceDataTab.signOut()

      signIn(Config.Users.ron)
      workspaceDataTab.open
      workspaceDataTab.ui.readColumnHeaders shouldEqual List("participant_id", "test2")
    }
  }
}
