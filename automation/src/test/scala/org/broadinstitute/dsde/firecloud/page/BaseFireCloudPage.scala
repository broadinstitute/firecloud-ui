package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.component.{Link, TestId}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.methodrepo.MethodRepoPage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.openqa.selenium.WebDriver

abstract class BaseFireCloudPage(implicit webDriver: WebDriver) extends AuthenticatedPage {

  def goToWorkspaces(): WorkspaceListPage = {
    Link(TestId("workspace-nav-link")).doClick()
    await ready new WorkspaceListPage
  }

  def goToDataLibrary(): DataLibraryPage = {
    Link(TestId("library-nav-link")).doClick()
    await ready new DataLibraryPage
  }

  def goToMethodRepository(): MethodRepoPage = {
    Link(TestId("method-repo-nav-link")).doClick()
    await ready new MethodRepoPage()
  }
}
