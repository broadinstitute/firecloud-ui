package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.BaseFireCloudPage
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigListPage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.WorkspaceMonitorPage
import org.broadinstitute.dsde.firecloud.page.workspaces.notebooks.WorkspaceNotebooksPage
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.openqa.selenium.WebDriver


/*
A Workspace Page is any page within the workspace (i.e. the Summary tab, Data tab)
 */
abstract class WorkspacePage(namespace: String, name: String)(implicit webDriver: WebDriver) extends BaseFireCloudPage {

  private val workspaceError = Label("workspace-details-error")

  private val namespaceHeader = Label("header-namespace")
  private val nameHeader = Label("header-name")
  private val tabs = TabBar()

  override def awaitReady(): Unit = await spinner "Loading workspace..."

  def isError: Boolean = workspaceError.isVisible
  def readError(): String = workspaceError.getText

  def readWorkspace: (String, String) = {
    (namespaceHeader.getText, nameHeader.getText)
  }

  def validateWorkspace: Boolean = {
    (namespace, name) == readWorkspace
  }

  def goToSummaryTab(): WorkspaceSummaryPage = {
    tabs.goToTab("Summary")
    await ready new WorkspaceSummaryPage(namespace, name)
  }

  def goToDataTab(): WorkspaceDataPage = {
    tabs.goToTab("Data")
    await ready new WorkspaceDataPage(namespace, name)
  }

  def goToMethodConfigTab(): WorkspaceMethodConfigListPage = {
    tabs.goToTab("Method Configurations")
    await ready new WorkspaceMethodConfigListPage(namespace, name)
  }

  def goToMonitorTab(): WorkspaceMonitorPage = {
    tabs.goToTab("Monitor")
    await ready new WorkspaceMonitorPage(namespace, name)
  }

  def goToNotebooksTab(): WorkspaceNotebooksPage = {
    tabs.goToTab("Notebooks")
    await ready new WorkspaceNotebooksPage(namespace, name)
  }
}
