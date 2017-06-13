import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceSummaryPage}
import org.broadinstitute.dsde.firecloud.workspaces.WorkspaceFixtures
import org.broadinstitute.dsde.firecloud.{CleanUp, Config, Util}
import org.scalatest._

class WorkspaceSpec extends FreeSpec with WebBrowserSpec with WorkspaceFixtures[WorkspaceSpec]
  with CleanUp with Matchers {

  "A user" - {
    "with a billing project" - {
      "should be able to create a workspace" in withWebDriver { implicit driver =>
        val projectName = "broad-dsde-dev"
        val workspaceName = "WorkspaceSpec_create_" + randomUuid
        implicit val authToken = AuthToken(Config.Accounts.testUser)

        val listPage = signIn(Config.Accounts.testUser)
        val detailPage = listPage.createWorkspace(projectName, workspaceName)
        register cleanUp api.workspaces.delete(projectName, workspaceName)

        detailPage.awaitLoaded()
        detailPage.ui.readWorkspaceName shouldEqual workspaceName

        listPage.open
        listPage.filter(workspaceName)
        listPage.ui.hasWorkspace(projectName, workspaceName) shouldBe true
      }

      "should be able to clone a workspace" in withWebDriver { implicit driver =>
        val billingProject = "broad-dsde-dev"
        val wsName = "WorkspaceSpec_to_be_cloned_" + randomUuid
        val wsNameCloned = "WorkspaceSpec_clone_" + randomUuid
        implicit val authToken = Config.AuthTokens.testFireC
        api.workspaces.create(billingProject, wsName)
        register cleanUp api.workspaces.delete(billingProject, wsName)

        val listPage = signIn(Config.Accounts.testFireC)
        val workspaceSummaryPage = new WorkspaceSummaryPage(billingProject, wsName).open
        workspaceSummaryPage.cloneWorkspace(billingProject, wsNameCloned)
        register cleanUp api.workspaces.delete(billingProject, wsNameCloned)

        listPage.open
        listPage.filter(wsNameCloned)
        listPage.ui.hasWorkspace(billingProject, wsNameCloned) shouldBe true
      }
    }

    "who owns a workspace" - {
      "should be able to delete the workspace" in withWebDriver { implicit driver =>
        val projectName = Config.Projects.common
        val workspaceName = "WorkspaceSpec_delete_" + Util.makeUuid
        implicit val authToken = AuthToken(Config.Accounts.testUser)

        api.workspaces.create(projectName, workspaceName)
        register cleanUp api.workspaces.delete(projectName, workspaceName)

        val listPage = signIn(Config.Accounts.testUser)
        val detailPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
        detailPage.deleteWorkspace().awaitLoaded()

        listPage.validateLocation()
        listPage.filter(workspaceName)
        listPage.ui.hasWorkspace(projectName, workspaceName) shouldBe false
      }
    }
  }

  // Experimental
  "A cloned workspace" - {
    "should retain the source workspace's authorization domain" in withWebDriver { implicit driver =>
      implicit val authToken: AuthToken = Config.AuthTokens.testFireC
      withClonedWorkspace(Config.Projects.common, Option("AuthDomainSpec_share")) { cloneWorkspaceName =>
        val listPage = signIn(Config.Accounts.testFireC)
        val summaryPage = listPage.openWorkspaceDetails(Config.Projects.common, cloneWorkspaceName).awaitLoaded()
        // assert that user who cloned the workspace is the owner
      }
    }
  }
}
