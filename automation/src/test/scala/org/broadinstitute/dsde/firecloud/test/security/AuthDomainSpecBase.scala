package org.broadinstitute.dsde.firecloud.test.security

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.RequestAccessModal
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.{BillingFixtures, GroupFixtures, WorkspaceFixtures}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{FreeSpec, Matchers}

class AuthDomainSpecBase extends FreeSpec with Matchers with WebBrowserSpec with CleanUp with WorkspaceFixtures
    with BillingFixtures with GroupFixtures with UserFixtures {


  val projectName: String = Config.Projects.common
  /*
   * Unless otherwise declared, this auth token will be used for API calls.
   * We are using a curator to prevent collisions with users in tests (who are Students and AuthDomainUsers), not
   *  because we specifically need a curator.
   */
  val defaultUser: Credentials = UserPool.chooseCurator
  val authTokenDefault: AuthToken = defaultUser.makeAuthToken()

  /**
    *
    * @param wsSummaryPage: Workspace Summary page
    * @param workspaceName: Workspace name
    * @param projName: Project name
    */
  protected def checkWorkspaceFailure(wsSummaryPage: WorkspaceSummaryPage, workspaceName: String, projName: String = projectName): Unit = {
    val error = wsSummaryPage.readError()
    error should include(projName)
    error should include(workspaceName)
    error should include("does not exist")
  }


  def checkNoAccess(user: Credentials, projectName: String, workspaceName: String): Unit = {
    withWebDriver { implicit driver =>
      withSignIn(user) { workspaceListPage =>
        // Not in workspace list
        workspaceListPage.hasWorkspace(projectName, workspaceName) shouldBe false

        // No direct access
        val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
        checkWorkspaceFailure(workspaceSummaryPage, workspaceName, projectName)
      }
    }
  }

  def checkVisibleNotAccessible(user: Credentials, projectName: String, workspaceName: String): Unit = {
    withWebDriver { implicit driver =>
      withSignIn(user) { workspaceListPage =>
        // Looks restricted; implies in workspace list
        workspaceListPage.looksRestricted(projectName, workspaceName) shouldEqual true

        // Clicking opens request access modal
        workspaceListPage.clickWorkspaceLink(projectName, workspaceName)
        workspaceListPage.showsRequestAccessModal() shouldEqual true
        // TODO: THIS IS BAD! However, the modal does some ajax loading which could cause the button to move causing the click to fail. This needs to be fixed inside RequestAccessModal.
        Thread sleep 500
        new RequestAccessModal().cancel()

        // No direct access
        val workspaceSummaryPage = new WorkspaceSummaryPage(projectName, workspaceName).open
        checkWorkspaceFailure(workspaceSummaryPage, workspaceName, projectName)
      }
    }
  }

  def checkVisibleAndAccessible(user: Credentials, projectName: String, workspaceName: String): Unit = {
    withWebDriver { implicit driver =>
      withSignIn(user) { workspaceListPage =>
        // Looks restricted; implies in workspace list
        workspaceListPage.looksRestricted(projectName, workspaceName) shouldEqual true

        // Clicking opens workspace
        workspaceListPage.enterWorkspace(projectName, workspaceName).validateLocation()

        // Direct access also works
        // Navigate somewhere else first otherwise background login status gets lost
        workspaceListPage.open
        new WorkspaceSummaryPage(projectName, workspaceName).open.validateLocation()
      }
    }
  }

}
