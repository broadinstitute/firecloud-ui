package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel
import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.firecloud.component.{Button, Collapse, Table}
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

  override def awaitReady(): Unit = {
    await condition {
      enabled(testId("workspace-details-error")) ||
      (enabled(testId("submission-status")) && getState == "ready")
    }
    await spinner "Loading..."
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
    val cloneModal = ui.clickCloneButton()
    cloneModal.cloneWorkspace(billingProjectName, workspaceName, authDomain)
  }

  def unpublishWorkspace(): Unit = {
    ui.clickUnpublishButton()
    val msgModal = await ready new MessageModal
    msgModal.clickOk()
  }

  /**
    * Deletes the workspace currently being viewed. Returns while transitioning
    * to the resulting view after successful deletion.
    */
  def deleteWorkspace(): WorkspaceListPage = {
    val workspaceDeleteModal = ui.clickDeleteWorkspaceButton()
    workspaceDeleteModal.confirmDelete()
    await ready new WorkspaceListPage
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
    await ready new WorkspaceSummaryPage(namespace, name)
  }

  def openShareDialog(email: String, accessLevel: String): AclEditor = {
    await notVisible spinner
    val aclEditor = ui.clickShareWorkspaceButton()
    aclEditor.fillEmailAndAccess(email, WorkspaceAccessLevel.withName(accessLevel))
    aclEditor
  }

  trait UI {
    private val authDomainGroups = testId("auth-domain-groups")
    private val authDomainRestrictionMessage = testId("auth-domain-restriction-message")

    private val editButton = Button("edit-button")
    private val saveButton = Button("save-button")
    private val cancelButton = Button("cancel-editing-button")

    private val cloneButton = Button("open-clone-workspace-modal-button")
    private val deleteWorkspaceButton = Button("delete-workspace-button")
    private val publishButton = Button("publish-button")
    private val unpublishButton = Button("unpublish-button")
    private val shareWorkspaceButton = Button("share-workspace-button")

    private val workspaceError = testId("workspace-details-error")
    private val accessLevel = testId("workspace-access-level")

    private val workspaceAttributesArea = Collapse("attribute-editor", new FireCloudView {
      override def awaitReady(): Unit = table.awaitReady()

      val newButton: Query = testId("add-new-button")
      val table = Table("workspace-attributes")

      def clickNewButton(): Unit = click on newButton
    })
    import scala.language.reflectiveCalls

    def clickCloneButton(): CloneWorkspaceModal = {
      cloneButton.doClick()
      await ready new CloneWorkspaceModal
    }

    def clickDeleteWorkspaceButton(): DeleteWorkspaceModal = {
      deleteWorkspaceButton.doClick()
      await ready new DeleteWorkspaceModal
    }

    def clickPublishButton(): Unit = {
      publishButton.doClick()
    }

    def clickUnpublishButton(): Unit = {
      unpublishButton.doClick()
    }

    def clickShareWorkspaceButton(): AclEditor = {
      shareWorkspaceButton.doClick()
      await ready new AclEditor
    }

    def hasShareButton: Boolean = {
      shareWorkspaceButton.isVisible
    }

    def hasPublishButton: Boolean = {
      publishButton.isVisible
    }

    def hasUnpublishButton: Boolean = {
      unpublishButton.isVisible
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
      !editButton.isVisible
    }

    def beginEditing(): Unit = {
      if (!isEditing)
        editButton.doClick()
      else
        throw new IllegalStateException("Already editing")
    }

    def save(): Unit = {
      if (isEditing) {
        saveButton.doClick()
        awaitReady()
      }
      else
        throw new IllegalStateException("Tried to click on 'save' while not editing")
    }

    def cancelEdit(): Unit = {
      if (isEditing) {
        cancelButton.doClick()
        awaitReady()
      }
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
