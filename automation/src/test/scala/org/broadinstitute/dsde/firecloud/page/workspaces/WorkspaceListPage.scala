package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{AuthenticatedPage, FireCloudView, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Workspace List page.
  */
class WorkspaceListPage(implicit webDriver: WebDriver) extends AuthenticatedPage
    with Page with PageUtil[WorkspaceListPage] {
  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces"

  /**
    * Creates a new workspace. Returns a new WorkspaceSummaryPage.
    *
    * @param billingProjectName the billing project for the workspace (aka namespace)
    * @param workspaceName the name for the new workspace
    * @param authDomain the authorization domain for the new workspace
    * @return a WorkspaceSummaryPage for the created workspace
    */
  def createWorkspace(billingProjectName: String, workspaceName: String,
                      authDomain: Option[String] = None): WorkspaceSummaryPage = {
    ui.clickCreateWorkspaceButton()
          .createWorkspace(billingProjectName, workspaceName, authDomain)
    new WorkspaceSummaryPage(billingProjectName, workspaceName)
  }

  /**
    * Filters the list of workspaces.
    *
    * @param text the text to filter by
    */
  def filter(text: String): Unit = {
    ui.fillFilterText(text)
    ui.clickFilterButton()
  }

  /**
    * Opens the workspace details for a workspace. The workspace must be
    * visible in the workspace list. Therefore, it is recommended to first
    * filter on the workspace name.
    *
    * @param namespace the workspace namespace
    * @param name the workspace name
    * @return a WorkspaceDetailPage for the selected workspace
    */
  def openWorkspaceDetails(namespace: String, name: String): WorkspaceSummaryPage = {
    filter(name)
    ui.clickWorkspaceInList(namespace, name)
    new WorkspaceSummaryPage(namespace, name)
  }

  def validateLocation(): Unit = {
    assert(ui.filterInputIsPresent())
  }

  def isLoaded: Boolean = {
    ui.filterInputIsPresent()
  }

  override def awaitLoaded(): WorkspaceListPage = {
    await condition isLoaded
    this
  }


  trait UI extends super.UI {
    private val createWorkspaceButton = testId("open-create-workspace-modal-button")
    private val filterButton = testId("workspace-list-filter-button")
    private val filterInput = testId("workspace-list-filter-input")
    private def restrictedWorkspaceTestId(ns: String, n: String) = { s"restricted-$ns-$n" }

    def clickCreateWorkspaceButton(): CreateWorkspaceModal = {
      click on (await enabled createWorkspaceButton)
      new CreateWorkspaceModal
    }

    def clickFilterButton(): Unit = {
      click on (await enabled filterButton)
    }

    def clickWorkspaceInList(namespace: String, name: String): Unit = {
      click on title(s"$namespace/$name")
    }

    def fillFilterText(text: String): Unit = {
      await enabled filterInput
      searchField(filterInput).value = text
    }

    def filterInputIsPresent(): Boolean = {
      find(filterInput).isDefined
    }

    def hasWorkspace(namespace: String, name: String): Boolean = {
      find(title(s"$namespace/$name")).isDefined
    }

    def looksRestricted(namespace: String, name: String): Boolean = {
      find(testId(restrictedWorkspaceTestId(namespace, name))).isDefined
    }
  }
  object ui extends UI
}

/**
  * Page class for the create workspace modal.
  */
class CreateWorkspaceModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    * Creates a new workspace. Returns after the FireCloud busy spinner
    * disappears.
    *
    * @param workspaceName the name for the new workspace
    * @param billingProjectName the billing project for the workspace
    */
  def createWorkspace(billingProjectName: String, workspaceName: String, authDomain: Option[String] = None): Unit = {
    ui.selectBillingProject(billingProjectName)
    ui.fillWorkspaceName(workspaceName)
    authDomain foreach { ui.selectAuthDomain(_) }

    ui.clickCreateWorkspaceButton()
    createWorkspaceWait()
  }

  def createWorkspaceWait(): Unit = {
    // Micro-sleep to make sure the spinner has had a chance to render
    Thread sleep 200
    await notVisible spinner
  }


  object ui {
    private val authDomainSelect = testId("workspace-auth-domain-select")
    private val billingProjectSelect = testId("billing-project-select")
    private val createWorkspaceButton: Query = testId("create-workspace-button")
    private val workspaceNameInput: Query = testId("workspace-name-input")

    def clickCreateWorkspaceButton(): Unit = {
      click on createWorkspaceButton
    }

    def fillWorkspaceName(workspaceName: String): Unit = {
      textField(workspaceNameInput).value = workspaceName
    }

    def selectAuthDomain(authDomain: String): Unit = {
      singleSel(authDomainSelect).value = option value authDomain
    }

    def selectBillingProject(billingProjectName: String): Unit = {
      singleSel(billingProjectSelect).value = option value billingProjectName
    }
  }
}
