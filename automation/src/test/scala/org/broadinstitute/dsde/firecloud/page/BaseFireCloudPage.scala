package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.methodrepo.MethodRepoPage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.openqa.selenium.WebDriver

abstract class BaseFireCloudPage(implicit webDriver: WebDriver) extends AuthenticatedPage {

  def goToWorkspaces(): WorkspaceListPage = {
    click on testId("workspace-nav-link")
    await ready new WorkspaceListPage
  }

  def goToDataLibrary(): DataLibraryPage = {
    click on testId("library-nav-link")
    await ready new DataLibraryPage
  }

  def goToMethodRepository(): MethodRepoPage = {
    click on testId("method-repo-nav-link")
    await ready new MethodRepoPage()
  }
}
