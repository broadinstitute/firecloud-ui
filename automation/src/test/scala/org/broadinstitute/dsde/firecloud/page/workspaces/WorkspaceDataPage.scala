package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.component.{Button, FileSelector, Label, Table}
import org.broadinstitute.dsde.firecloud.page.{OKCancelModal, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page


class WorkspaceDataPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceDataPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/data"

  override def awaitReady(): Unit = {
    dataTable.awaitReady()
  }

  private val dataTable = Table("entity-table")
  private val importMetadataButton = Button("import-metadata-button")

  def importFile(file: String): Unit = {
    importMetadataButton.doClick()
    val importModal = await ready new ImportMetadataModal
    importModal.importFile(file)
    dataTable.awaitReady()
  }

  def getNumberOfParticipants: Int = {
    // TODO: click on the tab and read the actual table size
    dataTable.readDisplayedTabCount("participant")
  }
}

/**
  * Page class for the import data modal.
  */
class ImportMetadataModal(implicit webDriver: WebDriver) extends OKCancelModal {
  override def awaitReady(): Unit = importFromFileButton.isVisible

  private val importFromFileButton = Button("import-from-file-button")
  private val fileUploadInput = FileSelector("data-upload-input")
  private val confirmUploadMetadataButton = Button("confirm-upload-metadata-button")
  private val uploadSuccessMessage = Label("upload-success-message")

  /**
    * Confirms the request to delete a workspace. Returns after the FireCloud
    * busy spinner disappears.
    */
  def importFile(file: String): Unit = {
    importFromFileButton.doClick()
    fileUploadInput.selectFile(file)
    confirmUploadMetadataButton.doClick()
    uploadSuccessMessage.awaitVisible()
    xOut()
  }
}

