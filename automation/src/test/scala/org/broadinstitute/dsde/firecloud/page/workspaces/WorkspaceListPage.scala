package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.page.{AuthenticatedPage, OKCancelModal, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Workspace List page.
  */
class WorkspaceListPage(implicit webDriver: WebDriver) extends AuthenticatedPage
    with Page with PageUtil[WorkspaceListPage] {
  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces"

  override def awaitReady(): Unit = {
    workspacesTable.awaitReady()
  }

  private val workspacesTable = Table("workspace-list")
  private val createWorkspaceButton = Button("open-create-workspace-modal-button")
  private val requestAccessModal = testId("request-access-modal")
  private def workspaceLink(ns: String, n: String) = Link(s"$ns-$n-workspace-link")
  private def restrictedWorkspaceLabel(ns: String, n: String) = Label(s"restricted-$ns-$n")

  def clickCreateWorkspaceButton(): CreateWorkspaceModal = {
    createWorkspaceButton.doClick()
    await ready new CreateWorkspaceModal
  }

  /**
    * Creates a new workspace. Returns a new WorkspaceSummaryPage.
    *
    * @param billingProjectName the billing project for the workspace (aka namespace)
    * @param workspaceName the name for the new workspace
    * @param authDomain the authorization domain for the new workspace
    * @return a WorkspaceSummaryPage for the created workspace
    */
  def createWorkspace(billingProjectName: String, workspaceName: String,
                      authDomain: Set[String] = Set.empty): WorkspaceSummaryPage = {
    clickCreateWorkspaceButton().createWorkspace(billingProjectName, workspaceName, authDomain)
    await ready new WorkspaceSummaryPage(billingProjectName, workspaceName)
  }

  private def filter(text: String): Unit = {
    workspacesTable.filter(text)
  }

  def hasWorkspace(namespace: String, name: String): Boolean = {
    filter(name)
    workspaceLink(namespace, name).isVisible
  }

  def showsRequestAccessModal(): Boolean = {
    find(requestAccessModal).isDefined
  }

  def looksRestricted(namespace: String, name: String): Boolean = {
    filter(name)
    restrictedWorkspaceLabel(namespace, name).isVisible
  }

  /**
    * Filter to and select a given workspace
    *
    * @param namespace the workspace namespace
    * @param name the workspace name
    * @return a WorkspaceDetailPage for the selected workspace
    */
  def enterWorkspace(namespace: String, name: String): WorkspaceSummaryPage = {
    filter(name)
    workspaceLink(namespace, name).doClick()
    await ready new WorkspaceSummaryPage(namespace, name)
  }

  def validateLocation(): Unit = {
    assert(createWorkspaceButton.isVisible)
  }
}

/**
  * Page class for the create workspace modal.
  */
class CreateWorkspaceModal(implicit webDriver: WebDriver) extends OKCancelModal {
  private val authDomainSelect = Select("workspace-auth-domain-select")
  private val billingProjectSelect = Select("billing-project-select")
  private val workspaceNameInput = TextField("workspace-name-input")

  /**
    * Creates a new workspace. Returns after the FireCloud busy spinner
    * disappears.
    *
    * @param workspaceName the name for the new workspace
    * @param billingProjectName the billing project for the workspace
    */
  def createWorkspace(billingProjectName: String, workspaceName: String, authDomain: Set[String] = Set.empty): Unit = {
    billingProjectSelect.select(billingProjectName)
    workspaceNameInput.setText(workspaceName)
    authDomain foreach { authDomainSelect.select }

    submit()
  }
}
