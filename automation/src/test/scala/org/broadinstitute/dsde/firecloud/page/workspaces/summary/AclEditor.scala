package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.broadinstitute.dsde.firecloud.component.{Button, Checkbox, EmailField, Select}
import org.broadinstitute.dsde.firecloud.page.OKCancelModal
import org.openqa.selenium.WebDriver

/**
  * Page class for the Acl Editor modal
  */
class AclEditor(implicit webDriver: WebDriver) extends OKCancelModal {
  private val addNewAclButton = Button("add-new-acl-button")
  private val newAclEmailField = EmailField("acl-add-email")
  private val roleDropdown = Select("role-dropdown-true")
  private val canShareBox = Checkbox("acl-share-true")
  private val canComputeBox = Checkbox("acl-compute-true")

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
    }
    if (compute) {
      canComputeBox.ensureChecked()
    }

    submit()
  }

  def fillEmailAndAccess(email: String, accessLevel: WorkspaceAccessLevel): Unit = {
    addNewAclButton.doClick()
    newAclEmailField.setText(email)
    roleDropdown.select(accessLevel.toString)
  }

  def canComputeEnabled: Boolean = canComputeBox.isEnabled

  def canComputeChecked: Boolean = canComputeBox.isChecked
}
