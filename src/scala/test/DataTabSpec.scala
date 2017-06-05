import org.broadinstitute.dsde.firecloud.pages.{WebBrowserSpec, WorkspaceListPage}
import org.broadinstitute.dsde.firecloud.{Config, WebBrowserUtil}
import org.scalatest.selenium.WebBrowser
import org.scalatest.{FlatSpec, ParallelTestExecution, ShouldMatchers}

class DataTabSpec extends FlatSpec with WebBrowserSpec with ParallelTestExecution with ShouldMatchers with WebBrowser with WebBrowserUtil {

  behavior of "Data"

  it should "import a participants file" in withWebDriver { implicit driver =>
    signIn(Config.Accounts.testUserEmail, Config.Accounts.testUserPassword)

    val workspaceListPage = new WorkspaceListPage
    val workspaceName = "TestSpec_FireCloud_import_participants_file_" + randomUuid
    // replace this w/ workspace being created in the API?
    val workspaceSummaryTab = workspaceListPage.createWorkspace("broad-dsde-dev", workspaceName)
    val workspaceDataTab = workspaceSummaryTab.navigateToDataTab("broad-dsde-dev", workspaceName)
    //workspaceDataTab.import_file() ???
    workspaceSummaryTab.deleteWorkspace()
  }
}
