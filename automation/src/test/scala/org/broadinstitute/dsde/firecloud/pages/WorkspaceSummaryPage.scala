package org.broadinstitute.dsde.firecloud.pages

import org.broadinstitute.dsde.firecloud.{Config, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Workspace Detail page.
  */
class WorkspaceSummaryPage(namespace: String, name: String)(implicit webDriver: WebDriver) extends WorkspacePage with Page with PageUtil[WorkspaceSummaryPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name"

  override def awaitLoaded(): WorkspaceSummaryPage = {
    await enabled testId("submission-status")
    this
  }

  /**
    * Dictionary of access level labels displayed in the web UI.
    */
  object AccessLevel extends Enumeration {
    val NoAccess = Value("NO ACCESS")
    val Owner = Value("OWNER")
    val Reader = Value("READER")
    val Writer = Value("WRITER")
  }

  /**
    * Deletes the workspace currently being viewed. Returns while transitioning
    * to the resulting view after successful deletion.
    */
  def deleteWorkspace(): WorkspaceListPage = {
    val worskspaceDeleteModal = ui.clickDeleteWorkspaceButton()
    worskspaceDeleteModal.confirmDelete()
    new WorkspaceListPage
  }

  def share(emailField: String, accessLevel: AccessLevel.Value) = {
    ui.clickShareWorkspaceButton()
    // TODO: finish this
  }

  def clone(billingProjectName: String, newWorkspaceName: String, authDomain: Option[String] = None): WorkspaceSummaryPage = {
    ui.clickCloneWorkspaceButton().cloneWorkspace(billingProjectName, newWorkspaceName, authDomain)
  }


  trait UI extends super.UI {
    private val authDomainRestrictionMessage = testId("auth-domain-restriction-message")
    private val deleteWorkspaceButtonQuery = testId("delete-workspace-button")
    private val nameHeader = testId("header-name")
    private val publishButtonQuery = testId("publish-button")
    private val shareWorkspaceButton = testId("share-workspace-button")
    private val cloneWorkspaceButton = testId("open-clone-workspace-modal-button")

    def clickDeleteWorkspaceButton(): WorkspaceDeleteModal = {
      click on (await enabled deleteWorkspaceButtonQuery)
      new WorkspaceDeleteModal
    }

    def clickPublishButton(): ErrorModal = {
      click on (await enabled publishButtonQuery)
      new ErrorModal
    }

    def clickShareWorkspaceButton(): Unit = {
      click on (await enabled shareWorkspaceButton)
    }

    def clickCloneWorkspaceButton(): CloneWorkspaceModal = {
      click on (await enabled cloneWorkspaceButton)
      new CloneWorkspaceModal
    }

    def hasPublishButton: Boolean = {
      find(publishButtonQuery).isDefined
    }

    def readAuthDomainRestrictionMessage: String = {
      readText(authDomainRestrictionMessage)
    }

    def readWorkspaceName: String = {
      readText(nameHeader)
    }
  }
  object ui extends UI
}


/**
  * Page class for the workspace delete confirmation modal.
  */
class WorkspaceDeleteModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    * Confirms the request to delete a workspace. Returns after the FireCloud
    * busy spinner disappears.
    */
  def confirmDelete(): Unit = {
    ui.clickConfirmDeleteButton()
    await toggle spinner
  }


  object ui {
    private val confirmDeleteButtonQuery: Query = testId("confirm-delete-workspace-button")

    def clickConfirmDeleteButton(): Unit = {
      click on (await enabled confirmDeleteButtonQuery)
    }
  }
}

class CloneWorkspaceModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    * Clones a new workspace. Returns after the FireCloud busy spinner
    * disappears.
    *
    * @param workspaceName the name for the new workspace
    * @param billingProjectName the billing project for the workspace
    */
  def cloneWorkspace(billingProjectName: String, workspaceName: String, authDomain: Option[String] = None): WorkspaceSummaryPage = {
    ui.selectBillingProject(billingProjectName)
    ui.fillWorkspaceName(workspaceName)
    authDomain foreach { ui.selectAuthDomain(_) }

    ui.clickCloneWorkspaceButton()
    await toggle(spinner, 15)
    new WorkspaceSummaryPage(billingProjectName, workspaceName)
  }

  object ui {
    private val authDomainSelect = testId("workspace-auth-domain-select")
    private val billingProjectSelect = testId("billing-project-select")
    private val cloneWorkspaceButton: Query = testId("clone-workspace-button")
    private val workspaceNameInput: Query = testId("workspace-name-input")

    def clickCloneWorkspaceButton(): Unit = {
      click on cloneWorkspaceButton
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
