package org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceNotebooksPage

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class WorkspaceNotebooksPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceNotebooksPage] {

  override def awaitReady(): Unit = sparkClustersTable.awaitReady()

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/notebooks"

  private val openCreateClusterModalButton: Button = Button("create-modal-button")
  private val sparkClustersTable = Table("spark-clusters-table")

  def createClusterButtonEnabled(): Boolean = openCreateClusterModalButton.isStateEnabled





}