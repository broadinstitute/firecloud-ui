package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.BaseFireCloudPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.WorkspaceMonitorPage
import org.broadinstitute.dsde.firecloud.page.workspaces.notebooks.WorkspaceNotebooksPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.openqa.selenium.{WebDriver, WebDriverException}


/*
A Workspace Page is any page within the workspace (i.e. the Summary tab, Data tab)
 */
abstract class WorkspacePage(namespace: String, name: String)(implicit webDriver: WebDriver) extends BaseFireCloudPage {

  private val workspaceError = Label("workspace-details-error")

  private val namespaceHeader = Label("header-namespace")
  private val nameHeader = Label("header-name")
  private val tabs = TabBar()

  override def awaitReady(): Unit = {
    await spinner "Loading workspace..."
  }

  def isError: Boolean = workspaceError.isVisible
  def readError(): String = workspaceError.getText

  def readWorkspace: (String, String) = {
    (namespaceHeader.getText, nameHeader.getText)
  }

  def validateWorkspace: Boolean = {
    (namespace, name) == readWorkspace
  }

  def clickTab(tabName: String, pageUrl: String): Unit = {
    try {
      tabs.goToTab(tabName)
    } catch {
      case e: WebDriverException =>
    }
    // determine whether to retry click by comparing url
    if (pageUrl != webDriver.getCurrentUrl) {
      tabs.goToTab(tabName)
    }
  }

  def goToSummaryTab(): WorkspaceSummaryPage = {
    clickTab("Summary", new WorkspaceSummaryPage(namespace, name).url)
    await ready new WorkspaceSummaryPage(namespace, name)
  }

  def goToDataTab(): WorkspaceDataPage = {
    clickTab("Data", new WorkspaceDataPage(namespace, name).url)
    await ready new WorkspaceDataPage(namespace, name)
  }

  def goToMethodConfigTab(): WorkspaceMethodConfigListPage = {
    clickTab("Method Configurations", new WorkspaceMethodConfigListPage(namespace, name).url)
    await ready new WorkspaceMethodConfigListPage(namespace, name)
  }

  def goToMonitorTab(): WorkspaceMonitorPage = {
    clickTab("Monitor", new WorkspaceMonitorPage(namespace, name).url)
    await ready new WorkspaceMonitorPage(namespace, name)
  }

  def goToNotebooksTab(): WorkspaceNotebooksPage = {
    clickTab("Notebooks", new WorkspaceNotebooksPage(namespace, name).url)
    await ready new WorkspaceNotebooksPage(namespace, name)
  }
}
