package org.broadinstitute.dsde.firecloud.page.methodcommon

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.openqa.selenium.{TimeoutException, WebDriver}


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
    importExportButton.awaitNotVisible() // wait for modal to dismiss
    try { await notVisible (cssSelector("[data-test-id=spinner]"), 30) } catch {
      case _: TimeoutException =>
        throw new TimeoutException(s"Timed out waiting for Spinner stop on page ${webDriver.getCurrentUrl}.")
    }
  }
}
