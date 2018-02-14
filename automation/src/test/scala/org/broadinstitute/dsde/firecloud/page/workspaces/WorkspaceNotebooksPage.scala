package org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceNotebooksPage

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class WorkspaceNotebooksPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceNotebooksPage] {

  override def awaitReady(): Unit = {
    await condition { (find(clustersTableTestId).isDefined && clustersTable.getState == "ready") || find(clusterErrorMessage).isDefined }
  }

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/notebooks"

  private val clustersTable = Table("spark-clusters-table")
  private val clustersTableTestId = testId("spark-clusters-table")
  private val clusterErrorMessage = testId("notebooks-error")
  private val sparkClustersHeader = testId("spark-clusters-title")
  private val openCreateClusterModalButton: Button = Button("create-modal-button")
  private def unWhitelistedMessage = s"is unauthorized"

  def createClusterButtonEnabled(): Boolean = openCreateClusterModalButton.isStateEnabled

  def checkUnauthorized: Unit = {
    awaitReady()
    await text unWhitelistedMessage
  }
}