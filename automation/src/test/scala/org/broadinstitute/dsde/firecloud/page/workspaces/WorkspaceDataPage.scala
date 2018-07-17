package org.broadinstitute.dsde.firecloud.page.workspaces

import java.io.File

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component.{Button, FileSelector, Label, Table}
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.fixture.DownloadFixtures
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page
import org.scalatest.time.{Millis, Seconds, Span}


class WorkspaceDataPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with DownloadFixtures with PageUtil[WorkspaceDataPage] {

  override implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(500, Millis)))
  override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#workspaces/$namespace/$name/data"

  override def awaitReady(): Unit = {
    dataTable.awaitReady()
  }

  val dataTable = Table("entity-table")
  private val importMetadataButton = Button("import-metadata-button")
  private val downloadMetadataButton = Button("download-metadata-button")
  val form = CssSelectorQuery(s"${dataTable.query.queryString} form")

  def importFile(filePath: String): Unit = {
    importMetadataButton.doClick()
    val importModal = await ready new ImportMetadataModal
    importModal.importFile(filePath)
    dataTable.awaitReady()
  }

  def importFile(file: File): Unit = {
    importFile(file.getAbsolutePath)
  }

  def downloadMetadata(downloadPath: Option[String]): Option[String] = {
    val entityType = find(CssSelectorQuery(downloadMetadataButton.query.queryString)).get.attribute("data-entity-type").get
    downloadFile(downloadPath, entityType + ".txt", Right(form))
  }

  def getNumberOfParticipants: Int = {
    // TODO: click on the tab and read the actual table size
    dataTable.readDisplayedTabCount("participant")
  }

}

/**
  * Page class for the import data modal.
  */
class ImportMetadataModal(implicit webDriver: WebDriver) extends OKCancelModal("import-metadata-modal") {
  override def awaitReady(): Unit = importFromFileButton.awaitVisible()

  private val importFromFileButton = Button("import-from-file-button" inside this)
  private val fileUploadInput = FileSelector("data-upload-input" inside this)
  private val confirmUploadMetadataButton = Button("confirm-upload-metadata-button" inside this)
  private val uploadSuccessMessage = Label("upload-success-message" inside this)
  private val chooseFileButton = Button("choose-file-button" inside this)
  private val previewText = CssSelectorQuery(this.query.queryString + " pre")

  /**
    * Imports metadata from a file.
    */
  def importFile(file: String): Unit = {
    importFromFileButton.doClick()
    fileUploadInput.selectFile(file)
    fileUploadInput.awaitNotVisible()
    await condition find(previewText).exists(_.underlying.getText.contains("entity"))

    confirmUploadMetadataButton.doClick()
    confirmUploadMetadataButton.awaitNotVisible()

    chooseFileButton.awaitEnabledState()
    uploadSuccessMessage.awaitVisible()

    xOut()
  }

}

