package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.workspaces.summary.WorkspaceSummaryPage
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver

/**
  * Page class for the Workspace List page.
  */
class WorkspaceListPage(implicit webDriver: WebDriver) extends BaseFireCloudPage with PageUtil[WorkspaceListPage] {

  lazy override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#workspaces"

  override def awaitReady(): Unit = {
    super.awaitReady()
    workspacesTable.awaitReady()
  }

  val workspacesTable = Table("workspace-list")

  private val createWorkspaceButton = Button("open-create-workspace-modal-button")
  private val requestAccessModal = testId("request-access-modal")
  private val noBillingProjectsModal = testId("no-billing-projects-message")
  private def workspaceLink(ns: String, n: String) = Link(s"$ns-$n-workspace-link")
  private def restrictedWorkspaceLabel(ns: String, n: String) = Label(s"restricted-$ns-$n")

  def clickCreateWorkspaceButton(expectDisabled: Boolean = false): Option[CreateWorkspaceModal] = {
    /* wait for the create-workspace button to have loaded its billing project info. When expectDisabled=false,
        this should be a noop, because the button text is "Create New Workspace" when enabled.
        When expectDisabled=true, we need this wait. The button is disabled while loading billing info,
        then remains disabled if billing info is loaded but has no valid projects. This method can't distinguish
        between those two modes of disabled without looking at the text.
     */
    await text "Create New Workspace"

    if(expectDisabled) assert(createWorkspaceButton.isStateDisabled)
    createWorkspaceButton.doClick()
    if(!expectDisabled) Option(await ready new CreateWorkspaceModal) else None
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
    clickCreateWorkspaceButton().get.createWorkspace(billingProjectName, workspaceName, authDomain)
    await ready new WorkspaceSummaryPage(billingProjectName, workspaceName)
  }

  def hasWorkspace(namespace: String, name: String): Boolean = {
    workspacesTable.filter(name)
    workspaceLink(namespace, name).isVisible
  }

  def showsRequestAccessModal(): Boolean = {
    find(requestAccessModal).isDefined
  }

  def showsNoBillingProjectsModal(): Boolean = {
    find(noBillingProjectsModal).isDefined
  }

  def looksRestricted(namespace: String, name: String): Boolean = {
    workspacesTable.filter(name)
    restrictedWorkspaceLabel(namespace, name).isVisible
  }

  def clickWorkspaceLink(namespace: String, name: String): Unit = {
    workspacesTable.filter(name)
    workspaceLink(namespace, name).doClick()
  }

  /**
    * Filter to and select a given workspace
    *
    * @param namespace the workspace namespace
    * @param name the workspace name
    * @return a WorkspaceDetailPage for the selected workspace
    */
  def enterWorkspace(namespace: String, name: String): WorkspaceSummaryPage = {
    clickWorkspaceLink(namespace, name)
    await ready new WorkspaceSummaryPage(namespace, name)
  }

  def validateLocation(): Unit = {
    assert(createWorkspaceButton.isVisible)
  }
}

/**
  * Page class for the create workspace modal.
  */
class CreateWorkspaceModal(implicit webDriver: WebDriver) extends OKCancelModal("create-new-workspace-modal") {
  private val authDomainSelect = Select("workspace-auth-domain-select" inside this)
  private val billingProjectSelect = Select("billing-project-select" inside this)
  private val workspaceNameInput = TextField("workspace-name-input" inside this)

  override def awaitReady(): Unit = billingProjectSelect.awaitVisible()

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

class RequestAccessModal(implicit webDriver: WebDriver) extends OKCancelModal("request-access-modal")
