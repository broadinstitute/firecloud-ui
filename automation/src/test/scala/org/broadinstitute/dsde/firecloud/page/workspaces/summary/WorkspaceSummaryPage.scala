package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.{WorkspaceListPage, WorkspacePage}
import org.broadinstitute.dsde.firecloud._
import org.broadinstitute.dsde.workbench.service.WorkspaceAccessLevel
import org.broadinstitute.dsde.workbench.service.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.workbench.service.test.Awaiter
import org.openqa.selenium.{TimeoutException, WebDriver}


/**
  * Page class for the Workspace Detail page.
  */
class WorkspaceSummaryPage(namespace: String, name: String)(implicit val webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with PageUtil[WorkspaceSummaryPage] with Stateful {

  override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#workspaces/$namespace/$name"

  override val query: Query = testId("summary-tab")

  override def awaitReady(): Unit = {
    super.awaitReady()
    if (!(isError || getState == "error")) {
      try {
        await condition {
          getState == "ready"
        }
      } catch {
        case e: TimeoutException => throw new TimeoutException(s"Timed out waiting for [$query] data-test-state == ready on page $url. Actural value is ${getState}", e)
      }

      await condition {
        sidebar.isReady
      }

      await condition {
        submissionCounter.isReady
      }

      /*
      await condition {
        ( getState == "ready" && sidebar.isReady && submissionCounter.isReady
          // && (if (storageCostEstimate.isVisible) storageCostEstimate.isReady else true)
          )
        // FIXME: Storage cost estimate hangs when cloning workspaces in AuthDomainSpec
      } */

      /* The workspace summary tab aggressively refreshes after it appears to be ready. Therefore,
       * it's possible for tests to start interacting with the page in ways that change state and for
       * that state to be reset when the refresh happens. When looking at test failures, this has the
       * appearance of selenium click actions being completely missed.
       *
       * Users typically won't be interacting with the page in this narrow window because of the time
       * it takes to locate the element they want to click and moving the pointer and clicking. While
       * not ideal for test code, sleeping briefly here is an acceptable work-around until we can fix
       * the tab to not refresh so aggressively.
       */
      Thread.sleep(1000)
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
    msgModal.submit()
  }

  /**
    * Deletes the workspace currently being viewed. Returns while transitioning
    * to the resulting view after successful deletion.
    */
  def deleteWorkspace(): WorkspaceListPage = {
    val workspaceDeleteModal = sidebar.clickDeleteWorkspace()
    workspaceDeleteModal.confirmDelete()
    await ready new WorkspaceListPage()
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
      val syncModal = await ready new SynchronizeMethodAccessModal()
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
    sidebar.saveButton.isVisible
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
      Link(s"$key-delete").doClick()
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

    action

    if (isEditing)
      sidebar.clickSave()
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
    try {
      await condition !parent.isEditing
      parent.awaitReady()
    } catch {
      case _: TimeoutException =>
        val errorModal = new ErrorModal()
        if (errorModal.isVisible) {
          import spray.json._
          val json = (errorModal.getMessageText).parseJson
          throw new TimeoutException(s"Error modal:\n${json.prettyPrint}")
        } else {
          throw new TimeoutException(s"Error when click Save on page ${webDriver.getCurrentUrl}.")
        }
    }
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
    content != null
  }
}
