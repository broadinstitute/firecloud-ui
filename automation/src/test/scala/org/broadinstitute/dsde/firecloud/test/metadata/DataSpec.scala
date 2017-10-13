package org.broadinstitute.dsde.firecloud.test.metadata

import org.broadinstitute.dsde.firecloud.config.{AuthToken, AuthTokens, Config}
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceDataPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec, WebBrowserUtil}
import org.broadinstitute.dsde.firecloud.fixture.{UserFixtures, WorkspaceFixtures}
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FlatSpec, ParallelTestExecution, ShouldMatchers}

class DataSpec extends FlatSpec with WebBrowserSpec
  with UserFixtures with WorkspaceFixtures with ParallelTestExecution
  with ShouldMatchers with WebBrowser with WebBrowserUtil with CleanUp {

  val billingProject = Config.Projects.default
  implicit lazy val authToken: AuthToken = AuthTokens.harry
  behavior of "Data"

  it should "import a participants file" in withWebDriver { implicit driver =>
    withWorkspace(billingProject, "TestSpec_FireCloud_import_participants_file_") { workspaceName =>
      withSignIn(Config.Users.harry) { _ =>
        val filename = "src/test/resources/participants.txt"
        val workspaceDataTab = new WorkspaceDataPage(billingProject, workspaceName).open
        workspaceDataTab.importFile(filename)
        assert(workspaceDataTab.getNumberOfParticipants() == 1)

      }
    }
    //more checks should be added here
  }
}
