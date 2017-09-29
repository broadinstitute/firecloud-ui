package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.broadinstitute.dsde.firecloud.page.components.TabBar
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.WorkspaceMonitorPage
import org.openqa.selenium.WebDriver


/*
A Workspace Page is any page within the workspace (i.e. the Summary tab, Data tab)
 */
class WorkspacePage(namespace: String, name: String)(implicit webDriver: WebDriver) extends AuthenticatedPage {

  def readWorkspace: (String, String) = {
    ui.readWorkspace
  }

  def validateWorkspace: Boolean = {
    (namespace, name) == readWorkspace
  }

  trait UI extends super.UI {
    private val namespaceHeader = testId("header-namespace")
    private val nameHeader = testId("header-name")

    private val tabs = TabBar()

    def readWorkspace: (String, String) = {
      (readText(namespaceHeader), readText(nameHeader))
    }

    def goToSummaryTab(): WorkspaceSummaryPage = {
      tabs.awaitEnabled()
      tabs.goToTab("Summary")
      new WorkspaceSummaryPage(namespace, name)
    }

    def goToDataTab(): WorkspaceDataPage = {
      tabs.awaitEnabled()
      tabs.goToTab("Data")
      new WorkspaceDataPage(namespace, name)
    }

    def goToMethodConfigTab(): WorkspaceMethodConfigListPage = {
      tabs.awaitEnabled()
      tabs.goToTab("Method Configurations")
      new WorkspaceMethodConfigListPage(namespace, name)
    }

    def goToMonitorTab(): WorkspaceMonitorPage = {
      tabs.awaitEnabled()
      tabs.goToTab("Monitor")
      new WorkspaceMonitorPage(namespace, name)
    }
  }

  private object ui extends UI
}
