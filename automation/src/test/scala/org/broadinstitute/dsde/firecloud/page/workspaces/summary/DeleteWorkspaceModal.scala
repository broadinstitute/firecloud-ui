package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.component.OKCancelModal
import org.openqa.selenium.WebDriver

/**
  * Page class for the workspace delete confirmation modal.
  */
class DeleteWorkspaceModal(implicit webDriver: WebDriver) extends OKCancelModal("confirm-delete-modal") {
  /**
    * Confirms the request to delete a workspace. Returns after the FireCloud
    * busy spinner disappears.
    */
  def confirmDelete(): Unit = {
    submit()
  }


}
