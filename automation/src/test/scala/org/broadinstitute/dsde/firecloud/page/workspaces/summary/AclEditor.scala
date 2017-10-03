package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.api.WorkspaceAccessLevel.WorkspaceAccessLevel
import org.openqa.selenium.WebDriver

/**
  * Page class for the Acl Editor modal
  */
class AclEditor(implicit webDriver: WebDriver) extends FireCloudView  {

  def clickOk(): Unit = {
    ui.clickOkButton()
  }

  def clickCancel(): Unit = {
    ui.clickCancelButton()
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
      ui.clickCanShare()
    }
    if (compute) {
      ui.clickCanCompute()
    }
    ui.clickOkButton()
    await notVisible spinner
  }

  def fillEmailAndAccess(email: String, accessLevel: WorkspaceAccessLevel): Unit = {
    ui.clickAddNewAclButton()
    ui.fillNewAclEmailField(email)
    ui.clickRoleDropdown()
    ui.clickRoleLink(accessLevel)
  }

  def clickDropdown(): Unit = {
    ui.clickRoleDropdown()
  }

  def clickRole(workspaceAccessLevel: WorkspaceAccessLevel): Unit = {
    ui.clickRoleLink(workspaceAccessLevel)
  }

  object ui {
    private val okButton: Query = testId("ok-button")
    private val cancelButton: Query = testId("cancel-button")
    private val addNewAclButton: Query = testId("add-new-acl-button")
    private val newAclEmailField: Query = testId("acl-add-email")
    private val roleDropdown: Query = testId("role-dropdown-true")
    private val canShareBox: Query = testId("acl-share-true")
    private val canComputeBox: Query = testId("acl-compute-true")
    //TODO: add more here for multiple user save
    def clickOkButton(): Unit = {
      click on (await enabled okButton)
    }
    def clickCancelButton(): Unit = {
      click on (await enabled cancelButton)
    }
    def clickAddNewAclButton(): Unit = {
      click on (await enabled addNewAclButton)
    }
    def fillNewAclEmailField(email: String): Unit = {
      emailField(ui.newAclEmailField).value = email
    }

    def clickRoleDropdown(): Unit = {
      click on (await enabled roleDropdown)
    }

    def clickRoleLink(workspaceAccessLevel: WorkspaceAccessLevel): Unit = {
      val role = workspaceAccessLevel.toString
      singleSel(roleDropdown).value = option value role
    }

    def clickCanShare(): Unit = {
      click on (await enabled canShareBox)
    }

    def clickCanCompute(): Unit = {
      click on (await enabled canComputeBox)
    }

    def canComputeEnabled(): Boolean = {
      enabled(canComputeBox)
    }

    def canComputeChecked(): Boolean = {
      checkbox(canComputeBox).isSelected
    }
  }
}
