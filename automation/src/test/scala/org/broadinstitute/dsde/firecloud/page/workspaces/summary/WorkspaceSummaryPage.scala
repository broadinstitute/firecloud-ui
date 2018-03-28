package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.{WorkspaceListPage, WorkspacePage}
import org.broadinstitute.dsde.firecloud.{FireCloudView, Stateful}
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.service.WorkspaceAccessLevel
import org.broadinstitute.dsde.workbench.service.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Workspace Detail page.
  */
class WorkspaceSummaryPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceSummaryPage] with Stateful {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name"

  override val query: Query = testId("summary-tab")

  override def awaitReady(): Unit = {
    super.awaitReady()
    await condition {
      isError || getState == "error" ||
        (getState == "ready" && sidebar.getState == "ready" && submissionCounter.getState == "ready"
          /*&& (if (storageCostEstimate.isVisible) storageCostEstimate.getState == "ready" else true)*/)
          // FIXME: Storage cost estimate hangs when cloning workspaces in AuthDomainSpec
    }
  }

  def validateLocation(): Unit = {
    assert(submissionStatusLabel.isVisible && sidebar.getState == "ready" && getState == "ready")
  }

  private val submissionStatusLabel = Label("submission-status")

  private val authDomainGroups = Label("auth-domain-groups")
  private val accessLevel = Label("workspace-access-level")
  private val noBucketAccess = Label("no-bucket-access")
  private val googleBillingDetail = Label("google-billing-detail")

  def shouldWaitForBucketAccess: Boolean = {
    noBucketAccess.isVisible && noBucketAccess.getText.contains("unavailable")
  }

  private val sidebar = new Component(TestId("sidebar")) with Stateful {
    override def awaitReady(): Unit = {
      awaitState("ready")
    }

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
      WorkspaceSummaryPage.this.awaitReady()
    }

    def clickSave(): Unit = {
      saveButton.doClick()
      WorkspaceSummaryPage.this.awaitReady()
    }

    def clickCancel(): Unit = {
      cancelButton.doClick()
      WorkspaceSummaryPage.this.awaitReady()
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
      WorkspaceSummaryPage.this.awaitReady()
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

  private val storageCostEstimate = new Label("storage-cost-estimate") with Stateful {
    override def awaitReady(): Unit = awaitState("ready")
  }

  private val submissionCounter = new Label("submission-counter") with Stateful {
    override def awaitReady(): Unit = awaitState("ready")
  }

  private val workspaceAttributesArea = Collapse("attribute-editor", new FireCloudView {
    override def awaitReady(): Unit = {
      table.awaitReady()
    }

    val newButton = Button("add-new-button")
    val table = Table("workspace-attributes")

    def clickNewButton(): Unit = {
      newButton.doClick()
      awaitReady()
    }

    def clickForPreviewPane(link: String): PreviewModal = {
      click on LinkTextQuery(link)
      await ready new PreviewModal("preview-modal")
    }

  })
  import scala.language.reflectiveCalls

  /**
    * Dictionary of access level labels displayed in the web UI.
    */
  object AccessLevel extends Enumeration {
    type AccessLevel = Value
    val NoAccess: Value = Value("NO ACCESS")
    val Owner: Value = Value("OWNER")
    val Reader: Value = Value("READER")
    val Writer: Value = Value("WRITER")
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
      val syncModal = new SynchronizeMethodAccessModal("method-access")
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

  private def isEditing: Boolean = {
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

  def clickForPreview(link: String): PreviewModal = {
    workspaceAttributesArea.ensureExpanded()
    val previewPane = workspaceAttributesArea.getInner.clickForPreviewPane(link)
    previewPane
  }

  def edit(action: => Unit): Unit = {
    if (!isEditing)
      sidebar.clickEdit()
    else
      throw new IllegalStateException("Already editing")

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
