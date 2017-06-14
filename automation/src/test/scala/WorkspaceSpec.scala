import org.broadinstitute.dsde.firecloud.api.service
import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceSummaryPage}
import org.broadinstitute.dsde.firecloud.{CleanUp, Config, Util}
import org.scalatest._

class WorkspaceSpec extends FreeSpec with WebBrowserSpec with CleanUp
  with Matchers {

  "A user" - {
    "with a billing project" - {
      "should be able to create a workspace" in withWebDriver { implicit driver =>
        val projectName = "broad-dsde-dev"
        val workspaceName = "WorkspaceSpec_create_" + randomUuid
        implicit val authToken = Config.AuthTokens.testFireC

        val listPage = signIn(Config.Accounts.testFireC)
        val detailPage = listPage.createWorkspace(projectName, workspaceName)
        register cleanUp service.workspaces.delete(projectName, workspaceName)

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
        service.workspaces.create(billingProject, wsName)
        register cleanUp service.workspaces.delete(billingProject, wsName)

        val listPage = signIn(Config.Accounts.testFireC)
        val workspaceSummaryPage = new WorkspaceSummaryPage(billingProject, wsName).open
        workspaceSummaryPage.clone(billingProject, wsNameCloned)
        register cleanUp service.workspaces.delete(billingProject, wsNameCloned)

        listPage.open
        listPage.filter(wsNameCloned)
        listPage.ui.hasWorkspace(billingProject, wsNameCloned) shouldBe true
      }
    }

    "who owns a workspace" - {
      "should be able to delete the workspace" in withWebDriver { implicit driver =>
        val projectName = Config.Projects.common
        val workspaceName = "WorkspaceSpec_delete_" + Util.makeUuid
        implicit val authToken = Config.AuthTokens.testFireC

        service.workspaces.create(projectName, workspaceName)
        register cleanUp service.workspaces.delete(projectName, workspaceName)

        val listPage = signIn(Config.Accounts.testFireC)
        val detailPage = listPage.openWorkspaceDetails(projectName, workspaceName).awaitLoaded()
        detailPage.deleteWorkspace().awaitLoaded()

        listPage.validateLocation()
        listPage.filter(workspaceName)
        listPage.ui.hasWorkspace(projectName, workspaceName) shouldBe false
      }
    }
  }
}