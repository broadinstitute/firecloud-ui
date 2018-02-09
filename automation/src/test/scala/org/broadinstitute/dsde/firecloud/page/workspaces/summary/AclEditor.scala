package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.workbench.service.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.openqa.selenium.WebDriver

/**
  * Page class for the Acl Editor modal
  */
class AclEditor(implicit webDriver: WebDriver) extends OKCancelModal("acl-editor") {
  private val addNewAclButton = Button("add-new-acl-button" inside this)
  private val newAclEmailField = EmailField("acl-add-email" inside this)
  private val roleDropdown = Select("role-dropdown-true" inside this)
  val canShareBox = Checkbox("acl-share-true" inside this)
  val canComputeBox = Checkbox("acl-compute-true" inside this)

  override def awaitReady(): Unit = {
    addNewAclButton.awaitVisible()
  }

  /**
    * Shares workspace being viewed.
    * @param email email of user to be shared with
    * @param accessLevel accessLevel to set for user
    * @param share if the Can Share checkbox should be clicked
    * @param compute if the Can Compute checkbox should be clicked
    */
  def shareWorkspace(email: String, accessLevel: WorkspaceAccessLevel, share: Boolean, compute: Boolean): Unit = {
    fillEmailAndAccess(email, accessLevel)

    if (share) {
      canShareBox.ensureChecked()
    } else {
      canShareBox.ensureUnchecked()
    }

    if (compute) {
      canComputeBox.ensureChecked()
    } else {
      canComputeBox.ensureUnchecked()
    }

    submit()
  }

  def fillEmailAndAccess(email: String, accessLevel: WorkspaceAccessLevel): Unit = {
    addNewAclButton.doClick()
    newAclEmailField.setText(email)
    roleDropdown.select(accessLevel.toString)
  }

  def updateAccess(accessLevel: String): Unit = {
    roleDropdown.select(accessLevel)
  }
}
