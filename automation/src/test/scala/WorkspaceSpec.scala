import org.broadinstitute.dsde.firecloud.auth.{AuthToken, AuthTokens}
import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceSummaryPage}
import org.broadinstitute.dsde.firecloud.workspaces.WorkspaceFixtures
import org.broadinstitute.dsde.firecloud.{CleanUp, Config, Util}
import org.scalatest._

class WorkspaceSpec extends FreeSpec with WebBrowserSpec with WorkspaceFixtures[WorkspaceSpec]
  with CleanUp with Matchers {

  implicit val authToken: AuthToken = AuthTokens.harry
  val billingProject: String = Config.Projects.default

  "A user" - {
    "with a billing project" - {
      "should be able to create a workspace" in withWebDriver { implicit driver =>
        val workspaceName = "WorkspaceSpec_create_" + randomUuid

        val listPage = signIn(Config.Users.harry)
        val detailPage = listPage.createWorkspace(billingProject, workspaceName)
        register cleanUp api.workspaces.delete(billingProject, workspaceName)

        detailPage.awaitLoaded()
        detailPage.ui.readWorkspaceName shouldEqual workspaceName

        listPage.open
        listPage.filter(workspaceName)
        listPage.ui.hasWorkspace(billingProject, workspaceName) shouldBe true
      }

      "should be able to clone a workspace" in withWebDriver { implicit driver =>
        val wsName = "WorkspaceSpec_to_be_cloned_" + randomUuid
        val wsNameCloned = "WorkspaceSpec_clone_" + randomUuid
        api.workspaces.create(billingProject, wsName)
        register cleanUp api.workspaces.delete(billingProject, wsName)

        val listPage = signIn(Config.Users.harry)
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
        val workspaceName = "WorkspaceSpec_delete_" + Util.makeUuid

        api.workspaces.create(billingProject, workspaceName)
        register cleanUp api.workspaces.delete(billingProject, workspaceName)

        val listPage = signIn(Config.Users.harry)
        val detailPage = listPage.openWorkspaceDetails(billingProject, workspaceName).awaitLoaded()
        detailPage.deleteWorkspace().awaitLoaded()

        listPage.validateLocation()
        listPage.filter(workspaceName)
        listPage.ui.hasWorkspace(billingProject, workspaceName) shouldBe false
      }
    }
  }

  // Experimental
  "A cloned workspace" - {
    "should retain the source workspace's authorization domain" in withWebDriver { implicit driver =>
      withClonedWorkspace(Config.Projects.common, Option("AuthDomainSpec_share")) { cloneWorkspaceName =>
        val listPage = signIn(Config.Users.harry)
        val summaryPage = listPage.openWorkspaceDetails(Config.Projects.common, cloneWorkspaceName).awaitLoaded()
        // assert that user who cloned the workspace is the owner
      }
    }
  }
}
