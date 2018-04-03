package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.{WorkspaceListPage, WorkspacePage}
import org.broadinstitute.dsde.firecloud.{FireCloudView, ReadyComponent, SignalsReadiness, Stateful}
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.service.WorkspaceAccessLevel
import org.broadinstitute.dsde.workbench.service.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.workbench.service.test.Awaiter
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Workspace Detail page.
  */
class WorkspaceSummaryPage(namespace: String, name: String)(implicit val webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceSummaryPage] with Stateful {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name"

  override val query: Query = testId("summary-tab")

  override def awaitReady(): Unit = {
    super.awaitReady()
    await condition {
      isError || getState == "error" ||
        (getState == "ready" && sidebar.isReady && submissionCounter.isReady
          /*&& (if (storageCostEstimate.isVisible) storageCostEstimate.isReady else true)*/)
          // FIXME: Storage cost estimate hangs when cloning workspaces in AuthDomainSpec
    }
  }

  def validateLocation(): Unit = {
    assert(submissionStatusLabel.isVisible && sidebar.getState == "ready" && getState == "ready")
  }

  private val submissionStatusLabel = Label("submission-status")

  private val sidebar = new Sidebar(this)

  private val authDomainGroups = Label("auth-domain-groups")
  private val accessLevel = Label("workspace-access-level")
  private val noBucketAccess = Label("no-bucket-access")
  private val googleBillingDetail = Label("google-billing-detail")
  private val storageCostEstimate = new StorageCostEstimate()
  private val submissionCounter = new SubmissionCounter()
  private val workspaceAttributesArea = Collapse("attribute-editor", new WorkspaceAttributesArea())

  def shouldWaitForBucketAccess: Boolean = {
    noBucketAccess.isVisible && noBucketAccess.getText.contains("unavailable")
  }

  /**
    * Dictionary of access level labels displayed in the web UI.
    */
  object AccessLevel extends Enumeration {
    type AccessLevel = Value
    val NoAccess: AccessLevel = Value("NO ACCESS")
    val Owner: AccessLevel = Value("OWNER")
    val Reader: AccessLevel = Value("READER")
    val Writer: AccessLevel = Value("WRITER")
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
    val cloneModal = sidebar.clickClone()
    cloneModal.cloneWorkspace(billingProjectName, workspaceName, authDomain)
  }

  def unpublishWorkspace(): Unit = {
    val msgModal = sidebar.clickUnpublish()
    msgModal.clickOk()
  }

  /**
    * Deletes the workspace currently being viewed. Returns while transitioning
    * to the resulting view after successful deletion.
    */
  def deleteWorkspace(): WorkspaceListPage = {
    val workspaceDeleteModal = sidebar.clickDeleteWorkspace()
    workspaceDeleteModal.confirmDelete()
    await ready new WorkspaceListPage
  }

  /**
    * shares workspace currently being viewed with user email
    * @param email the email address of the user to add
    * @param accessLevel the access level to add the user as
    * @param share share permission
    * @param compute compute permission
    * @return the page, once sharing and sync are complete
    */
  def share(email: String, accessLevel: String, share: Boolean = false, compute: Boolean = false, grantMethodPermission: Option[Boolean] = None): WorkspaceSummaryPage = {
    val aclEditor = sidebar.clickShareWorkspaceButton()
    aclEditor.shareWorkspace(email, WorkspaceAccessLevel.withName(accessLevel), share, compute)
    if (grantMethodPermission.isDefined) {
      val syncModal = new SynchronizeMethodAccessModal()
      if (syncModal.validateLocation) {
        grantMethodPermission match {
          case Some(true) => syncModal.clickOk()
          case _ => syncModal.clickCancel()
        }
      }
    }
    awaitReady()
    this
  }

  def openShareDialog(email: String, accessLevel: String): AclEditor = {
    val aclEditor = sidebar.clickShareWorkspaceButton()
    aclEditor.fillEmailAndAccess(email, WorkspaceAccessLevel.withName(accessLevel))
    aclEditor
  }

  def clickPublishButton(expectSuccess: Boolean): OKCancelModal = {
    sidebar.clickPublish()
    if (expectSuccess) {
      // TODO: no test uses this yet
      null
    } else {
      await ready new MessageModal()
    }
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
    sidebar.clickClone()
    await ready new CloneWorkspaceModal
  }

  private[summary] def isEditing: Boolean = {
    !sidebar.editButton.isVisible
  }

  def hasGoogleBillingLink: Boolean = {
    googleBillingDetail.isVisible
  }

  def hasStorageCostEstimate: Boolean = {
    storageCostEstimate.isVisible
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

  def clickForPreview(link: String): GCSFilePreviewModal = {
    workspaceAttributesArea.ensureExpanded()
    workspaceAttributesArea.getInner.clickForPreviewPane(link)
  }

  def edit(action: => Unit): Unit = {
    if (!isEditing)
      sidebar.clickEdit()
    else
      throw new IllegalStateException("Already editing")

    val start = System.currentTimeMillis()
    while(!isEditing && System.currentTimeMillis() < start + 10000) {
      sidebar.clickEdit()
    }

    action

    if (isEditing) {
      sidebar.saveButton.scrollToVisible()
      sidebar.clickSave()
      awaitReady()
    }
    else
      throw new IllegalStateException("Tried to click on 'save' while not editing")
  }

  def readWorkspaceTable: List[List[String]] = {
    workspaceAttributesArea.getInner.table.getData
  }

  def readWorkspaceTableLinks: List[String] = {
    workspaceAttributesArea.getInner.table.getHref
  }
}

class Sidebar(parent: WorkspaceSummaryPage)(implicit val webDriver: WebDriver) extends Component("sidebar") with SignalsReadiness {
  val editButton = Button("edit-button" inside this)
  val saveButton = Button("save-button" inside this)
  val cancelButton = Button("cancel-editing-button" inside this)

  val cloneButton = Button("open-clone-workspace-modal-button" inside this)
  val deleteWorkspaceButton = Button("delete-workspace-button" inside this)
  val publishButton = Button("publish-button" inside this)
  val unpublishButton = Button("unpublish-button" inside this)
  val shareWorkspaceButton = Button("share-workspace-button" inside this)

  def clickEdit(): Unit = {
    editButton.doClick()
    await condition parent.isEditing
    parent.awaitReady()
  }

  def clickSave(): Unit = {
    saveButton.doClick()
    await condition !parent.isEditing
    parent.awaitReady()
  }

  def clickCancel(): Unit = {
    cancelButton.doClick()
    await condition !parent.isEditing
    parent.awaitReady()
  }

  def clickClone(): CloneWorkspaceModal = {
    cloneButton.doClick()
    await ready new CloneWorkspaceModal()
  }

  def clickDeleteWorkspace(): DeleteWorkspaceModal = {
    deleteWorkspaceButton.doClick()
    await ready new DeleteWorkspaceModal()
  }

  def clickPublish(): Unit = {
    publishButton.doClick()
    parent.awaitReady()
  }

  def clickUnpublish(): MessageModal = {
    unpublishButton.doClick()
    await ready new MessageModal()
  }

  def clickShareWorkspaceButton(): AclEditor = {
    shareWorkspaceButton.doClick()
    await ready new AclEditor()
  }
}

class StorageCostEstimate(implicit val webDriver: WebDriver) extends Label("storage-cost-estimate") with SignalsReadiness
class SubmissionCounter(implicit val webDriver: WebDriver) extends Label("submission-counter") with SignalsReadiness

class WorkspaceAttributesArea(implicit val webDriver: WebDriver) extends FireCloudView with ReadyComponent {
  val newButton = Button("add-new-button")
  val table = Table("workspace-attributes")

  override val readyComponent: Awaiter = table

  def clickNewButton(): Unit = {
    newButton.doClick()
    awaitReady()
  }

  def clickForPreviewPane(link: String): GCSFilePreviewModal = {
    click on LinkTextQuery(link)
    val previewModal = new GCSFilePreviewModal()
    // dos links take a sec to pop up
    previewModal.awaitVisible()
    await ready previewModal
  }
}

class SynchronizeMethodAccessModal(override implicit val webDriver: WebDriver) extends OKCancelModal("method-access") {
  def validateLocation: Boolean = {
    awaitReady()
    content != null
  }
}
