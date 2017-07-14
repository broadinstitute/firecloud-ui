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
    await condition { enabled(testId("submission-status")) || enabled(testId("workspace-details-error")) }
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

  def cloneWorkspace(billingProjectName: String, workspaceName: String, authDomain: Option[String] = None): WorkspaceSummaryPage = {
    val cloneModal = ui.clickCloneButton()
    cloneModal.cloneWorkspace(billingProjectName, workspaceName, authDomain)
    cloneModal.awaitCloneComplete()
    new WorkspaceSummaryPage(billingProjectName, workspaceName)
  }

  def unpublishWorkspace(): Unit = {
    ui.clickUnpublishButton()
    val msgModal = new MessageModal
    msgModal.clickOk()
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


  trait UI extends super.UI {
    private val authDomainRestrictionMessage = testId("auth-domain-restriction-message")
    private val cloneButton = testId("open-clone-workspace-modal-button")
    private val deleteWorkspaceButtonQuery = testId("delete-workspace-button")
    private val nameHeader = testId("header-name")
    private val publishButtonQuery = testId("publish-button")
    private val unpublishButtonQuery = testId("unpublish-button")
    private val shareWorkspaceButton = testId("share-workspace-button")
    private val workspaceError = testId("workspace-details-error")

    def clickCloneButton(): CloneWorkspaceModal = {
      click on (await enabled cloneButton)
      new CloneWorkspaceModal
    }

    def clickDeleteWorkspaceButton(): DeleteWorkspaceModal = {
      click on (await enabled deleteWorkspaceButtonQuery)
      new DeleteWorkspaceModal
    }

    def clickPublishButton(): Unit = {
      click on (await enabled publishButtonQuery)
    }

    def clickUnpublishButton(): Unit = {
      click on (await enabled unpublishButtonQuery)
    }

    def clickShareWorkspaceButton(): Unit = {
      click on (await enabled shareWorkspaceButton)
    }

    def hasPublishButton: Boolean = {
      find(publishButtonQuery).isDefined
    }

    def hasUnpublishButton: Boolean = {
      find(unpublishButtonQuery).isDefined
    }

    def hasWorkspaceNotFoundMessage: Boolean = {
      find(withText(s"$namespace/$name does not exist")).isDefined
    }

    def readAuthDomainRestrictionMessage: String = {
      readText(authDomainRestrictionMessage)
    }

    def readError(): String = {
      readText(workspaceError)
    }

    def readWorkspaceName: String = {
      readText(nameHeader)
    }
  }
  object ui extends UI
}


class CloneWorkspaceModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    * Clones a new workspace. Returns immediately after submitting. Call awaitCloneComplete to wait for cloning to be done.
    *
    * @param workspaceName the name for the new workspace
    * @param billingProjectName the billing project for the workspace
    */
  def cloneWorkspace(billingProjectName: String, workspaceName: String, authDomain: Option[String] = None): Unit = {
    ui.selectBillingProject(billingProjectName)
    ui.fillWorkspaceName(workspaceName)
    authDomain foreach { ui.selectAuthDomain(_) }

    ui.clickCloneButton()
  }

  def awaitCloneComplete(): Unit = {
    await toggle(spinner, 30)
  }


  object ui {
    private val authDomainSelect = testId("workspace-auth-domain-select")
    private val billingProjectSelect = testId("billing-project-select")
    private val cloneButtonQuery: Query = testId("clone-workspace-button")
    private val presetAuthDomain: Query = testId("required-auth-domain")
    private val workspaceNameInput: Query = testId("workspace-name-input")

    def clickCloneButton(): Unit = {
      click on (await enabled cloneButtonQuery)
    }

    def fillWorkspaceName(name: String): Unit = {
      textField(workspaceNameInput).value = name
    }

    def readPresetAuthDomain(): Option[String] = {
      find(presetAuthDomain).map(_.text)
    }

    def selectAuthDomain(authDomain: String): Unit = {
      singleSel(authDomainSelect).value = option value authDomain
    }

    def selectBillingProject(billingProjectName: String): Unit = {
      singleSel(billingProjectSelect).value = option value billingProjectName
    }
  }
}


/**
  * Page class for the workspace delete confirmation modal.
  */
class DeleteWorkspaceModal(implicit webDriver: WebDriver) extends FireCloudView {

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
