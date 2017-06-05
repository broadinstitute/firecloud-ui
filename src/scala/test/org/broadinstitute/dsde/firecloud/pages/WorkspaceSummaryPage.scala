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


  trait UI extends super.UI {
    private val authDomainRestrictionMessage = testId("auth-domain-restriction-message")
    private val deleteWorkspaceButtonQuery = testId("delete-workspace-button")
    private val nameHeader = testId("header-name")
    private val publishButtonQuery = testId("publish-button")
    private val shareWorkspaceButton = testId("share-workspace-button")

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
