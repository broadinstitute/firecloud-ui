package org.broadinstitute.dsde.firecloud.test.metadata

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

  "A user's column display preferences should persist across sessions" in withWebDriver { implicit driver =>
    implicit val authToken: AuthToken = AuthTokens.ron
    withWorkspace(Config.Projects.default, "DataSpec_column_display_prefs") { workspaceName =>
      signIn(Config.Users.ron)

    }
  }
}
