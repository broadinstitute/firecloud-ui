package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel
import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.firecloud.component.{Collapse, Table}
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.workspaces.{WorkspaceListPage, WorkspacePage}
import org.broadinstitute.dsde.firecloud.page.{PageUtil, _}
import org.broadinstitute.dsde.firecloud.{FireCloudView, Stateful}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Workspace Detail page.
  */
class WorkspaceSummaryPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceSummaryPage] with Stateful {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name"

  override val element: Query = testId("summary-tab")

  override def awaitLoaded(): WorkspaceSummaryPage = {
    await condition {
      enabled(testId("workspace-details-error")) ||
      (enabled(testId("submission-status")) && getState == "ready")
    }
    await spinner "Loading..."
    this
  }

  /**
    * Dictionary of access level labels displayed in the web UI.
    */
  object AccessLevel extends Enumeration {
    type AccessLevel = Value
    val NoAccess = Value("NO ACCESS")
    val Owner = Value("OWNER")
    val Reader = Value("READER")
    val Writer = Value("WRITER")
  }

  /**
    * Clones the workspace currently being viewed. Returns when the clone
    * operation is complete.
    *
    * @param billingProjectName the billing project for the workspace (aka namespace)
    * @param workspaceName the name for the new workspace
    * @param authDomain the authorization domain for the new workspace
    * @return a WorkspaceSummaryPage for the created workspace
    */
  def cloneWorkspace(billingProjectName: String, workspaceName: String, authDomain: Set[String] = Set.empty): WorkspaceSummaryPage = {
    await notVisible spinner
    val cloneModal = ui.clickCloneButton()
    cloneModal.cloneWorkspace(billingProjectName, workspaceName, authDomain)
    cloneModal.cloneWorkspaceWait()
    cloneWorkspaceWait()
    new WorkspaceSummaryPage(billingProjectName, workspaceName)
  }

  /**
    * Wait for workspace clone to complete.
    *
    * Clone is initiated from the workspace summary page for the source
    * workspace and ends on the workspace summary page for the cloned
    * workspace. WorkspaceSummaryPage.awaitLoaded() will complete even if the
    * browser has not yet navigated to the cloned workspace which could cause
    * subsequent assertions to fail. This extra wait makes sure that the
    * browser has navigated somewhere else.
    */
  def cloneWorkspaceWait(): Unit = {
    await condition { currentUrl != url }
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
    val workspaceDeleteModal = ui.clickDeleteWorkspaceButton()
    workspaceDeleteModal.confirmDelete()
    workspaceDeleteModal.confirmDeleteWait()
    new WorkspaceListPage
  }

  /**
    * shares workspace currently being viewed with user email
    * @param email
    * @param accessLevel
    * @param share
    * @param compute
    * @return
    */

  def share(email: String, accessLevel: String, share: Boolean = false, compute: Boolean = false): WorkspaceSummaryPage = {
    await notVisible spinner
    val aclEditor = ui.clickShareWorkspaceButton()
    aclEditor.shareWorkspace(email, WorkspaceAccessLevel.withName(accessLevel), share, compute)
    new WorkspaceSummaryPage(namespace, name)
  }

  def openShareDialog(email: String, accessLevel: String): AclEditor = {
    await notVisible spinner
    val aclEditor = ui.clickShareWorkspaceButton()
    aclEditor.fillEmailAndAccess(email, WorkspaceAccessLevel.withName(accessLevel))
    aclEditor
  }

  trait UI extends super.UI {
    private val authDomainGroups = testId("auth-domain-groups")
    private val authDomainRestrictionMessage = testId("auth-domain-restriction-message")

    private val editButton = testId("edit-button")
    private val saveButton = testId("save-button")
    private val cancelButton = testId("cancel-editing-button")

    private val cloneButton = testId("open-clone-workspace-modal-button")
    private val deleteWorkspaceButtonQuery = testId("delete-workspace-button")
    private val publishButtonQuery = testId("publish-button")
    private val unpublishButtonQuery = testId("unpublish-button")
    private val shareWorkspaceButton = testId("share-workspace-button")
    private val workspaceError = testId("workspace-details-error")
    private val accessLevel = testId("workspace-access-level")

    private class WorkspaceAttributesArea extends FireCloudView {
      def clickNewButton(): Unit = {
        click on (await enabled ui.newButton)
      }

      def table: Table = {
        ui.table
      }

      trait UI {
        val newButton: Query = testId("add-new-button")
        val table = Table("workspace-attributes")
      }
      object ui extends UI
    }

    private val workspaceAttributesArea = Collapse("attribute-editor", new WorkspaceAttributesArea)

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

    def clickShareWorkspaceButton(): AclEditor = {
      click on (await enabled shareWorkspaceButton)
      new AclEditor
    }

    def hasShareButton: Boolean = {
      find(shareWorkspaceButton).isDefined
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

    def readAuthDomainGroups: String = {
      readText(authDomainGroups)
    }

    def readAuthDomainRestrictionMessage: String = {
      readText(authDomainRestrictionMessage)
    }

    def readError(): String = {
      readText(workspaceError)
    }

    def readAccessLevel(): WorkspaceAccessLevel = {
      WorkspaceAccessLevel.withName(readText(accessLevel).toUpperCase)
    }

    def isEditing: Boolean = {
      find(editButton).isEmpty
    }

    def beginEditing(): Unit = {
      if (!isEditing)
        click on editButton
      else
        throw new IllegalStateException("Already editing")
    }

    def save(): Unit = {
      if (isEditing) {
        click on saveButton
        awaitLoaded()
      }
      else
        throw new IllegalStateException("Tried to click on 'save' while not editing")
    }

    def cancelEdit(): Unit = {
      if (isEditing)
        click on cancelButton
      else
        throw new IllegalStateException("Tried to click on 'cancel' while not editing")
    }

    def addWorkspaceAttribute(key: String, value: String): Unit = {
      if (isEditing) {
        workspaceAttributesArea.getInner.clickNewButton()
        // focus should take us to the field
        pressKeys(key)
        pressKeys("\t")
        pressKeys(value)
      } else {
        throw new IllegalArgumentException("Tried to add workspace attribute while not editing")
      }
    }

    def deleteWorkspaceAttribute(key: String): Unit = {
      if (isEditing) {
        // TODO: should do through table
        workspaceAttributesArea.ensureExpanded()
        click on testId(s"$key-delete")
      } else {
        throw new IllegalArgumentException("Tried to delete a row while not editing")
      }
    }

    def readWorkspaceTable: List[List[String]] = {
      workspaceAttributesArea.getInner.table.getData
    }
  }
  object ui extends UI
}
