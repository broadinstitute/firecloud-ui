package org.broadinstitute.dsde.firecloud.authdomain

import org.broadinstitute.dsde.firecloud.api.{WorkspaceAccessLevel, service}
import org.broadinstitute.dsde.firecloud.pages.WebBrowserSpec
import org.broadinstitute.dsde.firecloud.workspaces.WorkspaceFixtures
import org.broadinstitute.dsde.firecloud.{CleanUp, Config}
import org.scalatest._

class AuthDomainSpec extends FreeSpec with ParallelTestExecution with Matchers
  with CleanUp with WebBrowserSpec with WorkspaceFixtures {

  "A user" - {
    "in a group" - {
      "should be able to create a workspace with an authorization domain" in withWebDriver { implicit driver =>
        val projectName = Config.Projects.common
        val workspaceName = "AuthDomainSpec_create_" + randomUuid
        val authDomain = "dbGapAuthorizedUsers"
        implicit val authToken = Config.AuthTokens.elvin

        val workspaceListPage = signIn(Config.Accounts.elvin)
        val workspaceDetailPage = workspaceListPage.createWorkspace(
          projectName, workspaceName, Option(authDomain))
        register cleanUp service.workspaces.delete(projectName, workspaceName)

        workspaceDetailPage.awaitLoaded()
        workspaceDetailPage.ui.readAuthDomainRestrictionMessage should include (authDomain)

        workspaceListPage.open.filter(workspaceName)
        workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true
      }
    }
  }

  "A workspace with an authorization domain" - {
    "when shared with a user who is NOT in the auth domain" - {
      "should see but not be able to access the workspace" in withWebDriver { implicit driver =>
        val projectName = Config.Projects.common
        implicit val authToken = Config.AuthTokens.elvin

        withWorkspace(projectName, Option("AuthDomainSpec_reject"),
                      Option("dbGapAuthorizedUsers")) { workspaceName =>
          service.workspaces.updateAcl(projectName, workspaceName,
            Config.Accounts.dominique.email, WorkspaceAccessLevel.Reader)

          val workspaceListPage = signIn(Config.Accounts.dominique)
          workspaceListPage.filter(workspaceName)
          workspaceListPage.ui.looksRestricted(projectName, workspaceName) shouldEqual true

          workspaceListPage.ui.clickWorkspaceInList(projectName, workspaceName)
          // micro-sleep just long enough to let the app navigate elsewhere if it's going to, which it shouldn't in this case
          Thread sleep 500
          workspaceListPage.validateLocation()
        }
      }
    }
  }
}
