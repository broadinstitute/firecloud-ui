package org.broadinstitute.dsde.firecloud.pages

import org.openqa.selenium.WebDriver


/*
A Workspace Page is any page within the workspace (i.e. the Summary tab, Data tab)
 */
class WorkspacePage(implicit webDriver: WebDriver) extends AuthenticatedPage {

  private val dataTabButtonQuery: Query = testId("data-tab")
  def navigateToDataTab(namespace: String, name: String): Unit = {
    click on (await enabled dataTabButtonQuery)
    await toggle spinner
    new WorkspaceDataPage(namespace, name)
  }

}
