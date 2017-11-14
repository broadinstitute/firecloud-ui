package org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceNotebooksPage



class WorkspaceNotebooksPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceNotebooksPage] {

  override def awaitReady(): Unit = sparkClustersTable.awaitReady()

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/notebooks"

  private val openCreateClusterModalButton: Button = Button("create-modal-button")
  private val sparkClustersTable = Table("spark-clusters-table")

  def createClusterButtonEnabled(): Boolean = openCreateClusterModalButton.isStateEnabled





}