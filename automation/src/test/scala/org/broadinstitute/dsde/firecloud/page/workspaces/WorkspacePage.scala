package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.openqa.selenium.WebDriver


/*
A Workspace Page is any page within the workspace (i.e. the Summary tab, Data tab)
 */
class WorkspacePage(implicit webDriver: WebDriver) extends AuthenticatedPage {

  def readWorkspace: (String, String) = {
    ui.readWorkspace
  }

  trait UI extends super.UI {
    private val namespaceHeader = testId("header-namespace")
    private val nameHeader = testId("header-name")

    def readWorkspace: (String, String) = {
      (readText(namespaceHeader), readText(nameHeader))
    }
  }

  private object ui extends UI
}
