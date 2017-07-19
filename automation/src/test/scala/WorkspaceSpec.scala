import org.broadinstitute.dsde.firecloud.auth.{AuthToken, AuthTokens}
import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceSummaryPage}
import org.broadinstitute.dsde.firecloud.workspaces.WorkspaceFixtures
import org.broadinstitute.dsde.firecloud.{CleanUp, Config, Util}
import org.scalatest._

class WorkspaceSpec extends FreeSpec with WebBrowserSpec with WorkspaceFixtures
  with CleanUp with Matchers {

  implicit val authToken: AuthToken = AuthTokens.harry
  val billingProject: String = Config.Projects.default

  "A user" - {
    "with a billing project" - {
      "should be able to create a workspace" in withWebDriver { implicit driver =>
        val listPage = signIn(Config.Users.harry)

        val workspaceName = "WorkspaceSpec_create_" + randomUuid
        register cleanUp api.workspaces.delete(billingProject, workspaceName)
        val detailPage = listPage.createWorkspace(billingProject, workspaceName).awaitLoaded()

        detailPage.ui.readWorkspaceName shouldEqual workspaceName

        listPage.open
        listPage.filter(workspaceName)
        listPage.ui.hasWorkspace(billingProject, workspaceName) shouldBe true
      }

      "should be able to clone a workspace" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_to_be_cloned") { workspaceName =>
          val listPage = signIn(Config.Users.harry)

          val workspaceNameCloned = "WorkspaceSpec_clone_" + randomUuid
          val workspaceSummaryPage = new WorkspaceSummaryPage(billingProject, workspaceName).open
          register cleanUp api.workspaces.delete(billingProject, workspaceNameCloned)
          workspaceSummaryPage.cloneWorkspace(billingProject, workspaceNameCloned).awaitLoaded()

          listPage.open
          listPage.filter(workspaceNameCloned)
          listPage.ui.hasWorkspace(billingProject, workspaceNameCloned) shouldBe true
        }
      }
    }

    "who owns a workspace" - {
      "should be able to delete the workspace" in withWebDriver { implicit driver =>
        withWorkspace(billingProject, "WorkspaceSpec_delete") { workspaceName =>
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
      withClonedWorkspace(Config.Projects.common, "AuthDomainSpec_share") { cloneWorkspaceName =>
        val listPage = signIn(Config.Users.harry)
        val summaryPage = listPage.openWorkspaceDetails(Config.Projects.common, cloneWorkspaceName).awaitLoaded()
        // assert that user who cloned the workspace is the owner
      }
    }
  }
}