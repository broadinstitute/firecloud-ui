package org.broadinstitute.dsde.firecloud.page.workspaces

import com.typesafe.scalalogging.LazyLogging
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
abstract class WorkspacePage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends BaseFireCloudPage with LazyLogging{

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

  private def clickTab(tabName: String, pageUrl: String): Unit = {
    tabs.goToTab(tabName)
    if (pageUrl != webDriver.getCurrentUrl) {
      logger.warn(s"Actual page url is not $pageUrl. Action of clicking Tab($tabName) possibily failed")
    }
  }

  def goToSummaryTab(): WorkspaceSummaryPage = {
    val page = new WorkspaceSummaryPage(namespace, name)
    clickTab("Summary", page.url)
    await ready page
  }

  def goToDataTab(): WorkspaceDataPage = {
    val page = new WorkspaceDataPage(namespace, name)
    clickTab("Data", page.url)
    await ready page
  }

  def goToMethodConfigTab(): WorkspaceMethodConfigListPage = {
    val page = new WorkspaceMethodConfigListPage(namespace, name)
    clickTab("Method Configurations", page.url)
    await ready page
  }

  def goToMonitorTab(): WorkspaceMonitorPage = {
    val page = new WorkspaceMonitorPage(namespace, name)
    clickTab("Monitor", page.url)
    await ready page
  }

  def goToNotebooksTab(): WorkspaceNotebooksPage = {
    val page = new WorkspaceNotebooksPage(namespace, name)
    clickTab("Notebooks", page.url)
    await ready page
  }
}
