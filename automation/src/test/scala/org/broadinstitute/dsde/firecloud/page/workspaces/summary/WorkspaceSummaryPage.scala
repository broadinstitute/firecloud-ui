package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel
import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.firecloud.component._
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
      (enabled(testId("submission-status")) && sidebar.getState == "ready" && getState == "ready")
    }
  }

  private val authDomainGroups = Label("auth-domain-groups")
  private val workspaceError = Label("workspace-details-error")
  private val accessLevel = Label("workspace-access-level")

  private val sidebar = new Component("sidebar") with Stateful {
    override def awaitReady(): Unit = getState == "ready"

    val editButton = Button("edit-button")
    val saveButton = Button("save-button")
    val cancelButton = Button("cancel-editing-button")

    val cloneButton = Button("open-clone-workspace-modal-button")
    val deleteWorkspaceButton = Button("delete-workspace-button")
    val publishButton = Button("publish-button")
    val unpublishButton = Button("unpublish-button")
    val shareWorkspaceButton = Button("share-workspace-button")
  }

  private val workspaceAttributesArea = Collapse("attribute-editor", new FireCloudView {
    override def awaitReady(): Unit = table.awaitReady()

    val newButton = Button("add-new-button")
    val table = Table("workspace-attributes")

    def clickNewButton(): Unit = newButton.doClick()
  })
  import scala.language.reflectiveCalls

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

  def readError(): String = {
    workspaceError.getText
  }

  def readAccessLevel(): WorkspaceAccessLevel = {
    WorkspaceAccessLevel.withName(accessLevel.getText.toUpperCase)
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
    sidebar.cloneButton.doClick()
    val cloneModal = await ready new CloneWorkspaceModal
    cloneModal.cloneWorkspace(billingProjectName, workspaceName, authDomain)
  }

  def unpublishWorkspace(): Unit = {
    sidebar.unpublishButton.doClick()
    val msgModal = await ready new MessageModal
    msgModal.clickOk()
  }

  /**
    * Deletes the workspace currently being viewed. Returns while transitioning
    * to the resulting view after successful deletion.
    */
  def deleteWorkspace(): WorkspaceListPage = {
    sidebar.deleteWorkspaceButton.doClick()
    val workspaceDeleteModal = await ready new DeleteWorkspaceModal
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
    sidebar.shareWorkspaceButton.doClick()
    val aclEditor = await ready new AclEditor
    aclEditor.shareWorkspace(email, WorkspaceAccessLevel.withName(accessLevel), share, compute)
    await ready new WorkspaceSummaryPage(namespace, name)
  }

  def openShareDialog(email: String, accessLevel: String): AclEditor = {
    sidebar.shareWorkspaceButton.doClick()
    val aclEditor = await ready new AclEditor
    aclEditor.fillEmailAndAccess(email, WorkspaceAccessLevel.withName(accessLevel))
    aclEditor
  }

  def clickPublishButton(): Unit = {
    sidebar.publishButton.doClick()
  }

  def hasPublishButton: Boolean = {
    sidebar.publishButton.isVisible
  }

  def hasShareButton: Boolean = {
    sidebar.shareWorkspaceButton.isVisible
  }

  def readAuthDomainGroups: String = {
    authDomainGroups.getText
  }

  def clickCloneButton(): CloneWorkspaceModal = {
    sidebar.cloneButton.doClick()
    await ready new CloneWorkspaceModal
  }

  private def isEditing: Boolean = {
    !sidebar.editButton.isVisible
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

  def edit(action: => Unit): Unit = {
    if (!isEditing)
      sidebar.editButton.doClick()
    else
      throw new IllegalStateException("Already editing")

    action

    if (isEditing) {
      sidebar.saveButton.doClick()
      awaitReady()
    }
    else
      throw new IllegalStateException("Tried to click on 'save' while not editing")
  }

  def readWorkspaceTable: List[List[String]] = {
    workspaceAttributesArea.getInner.table.getData
  }
}
