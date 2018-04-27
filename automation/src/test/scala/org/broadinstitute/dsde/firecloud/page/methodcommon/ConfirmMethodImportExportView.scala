package org.broadinstitute.dsde.firecloud.page.methodcommon

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.openqa.selenium.WebDriver

// https://youtu.be/LnIKiNAupRs?t=49s
class ConfirmMethodImportExportView(importing: Boolean)(implicit webDriver: WebDriver) extends FireCloudView {
  private val importExportButton = Button("import-export-confirm-button")

  // only exists for export:
  val workspaceSelector = WorkspaceSelector()

  override def awaitReady(): Unit = {
    if (importing)
      importExportButton.awaitVisible()
    else
      workspaceSelector.awaitReady()
  }

  def confirm(): Unit = {
    importExportButton.doClick()
    importExportButton.awaitNotVisible() // another way to wait for modal to dismiss
  }
}
