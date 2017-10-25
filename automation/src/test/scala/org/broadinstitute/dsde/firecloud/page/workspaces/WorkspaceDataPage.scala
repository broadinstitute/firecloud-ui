package org.broadinstitute.dsde.firecloud.page.workspaces

import java.io.File
import java.text.SimpleDateFormat

import org.broadinstitute.dsde.firecloud.component.{Button, FileSelector, Label, Table}
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{OKCancelModal, PageUtil}
import org.broadinstitute.dsde.firecloud.util.Util
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page


class WorkspaceDataPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceDataPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/data"

  override def awaitReady(): Unit = {
    dataTable.awaitReady()
  }

  val dataTable = Table("entity-table")
  private val importMetadataButton = Button("import-metadata-button")
  private val downloadMetadataButton = Button("download-metadata-button")

  def importFile(filePath: String): Unit = {
    importMetadataButton.doClick()
    val importModal = await ready new ImportMetadataModal
    importModal.importFile(filePath)
    dataTable.awaitReady()
  }

  def importFile(file: File): Unit = {
    importFile(file.getAbsolutePath)
  }

  /**
    * Downloads the metadata currently being viewed.
    *
    * If downloadPath is given, the file is given a timestamped name and moved from that location
    * into the "downloads" directory off the current working directory. This serves two purposes:
    *
    * 1. Archiving the file for later inspection when tests fail
    * 2. Keeping the browser download directory clean so that it doesn't auto-rename subsequent
    * downloads with the same filename
    *
    * @param downloadPath the directory where the browser saves downloaded files
    * @return the relative path to the moved download file, or None if downloadPath was not given
    */
  def downloadMetadata(downloadPath: Option[String] = None): Option[String] = {
    downloadMetadataButton.doClick()
    /*
     * Downloading a file will open another window while the download is in progress and
     * automatically close it when the download is complete.
     */
    await condition { windowHandles.size == 1 }

    for {
      path <- downloadPath
      entityType <- find(CssSelectorQuery(downloadMetadataButton.element.queryString)).get.attribute("data-entity-type")
    } yield archiveDownloadedFile(s"$path/$entityType.txt")

    def archiveDownloadedFile(sourcePath: String): String = {
      val date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new java.util.Date())
      val destFile = new File(sourcePath).getName + s".$date"
      val destPath = s"downloads/$destFile"
      Util.moveFile(sourcePath, destPath)
      destPath
    }
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
    * Imports metadata from a file.
    */
  def importFile(file: String): Unit = {
    importFromFileButton.doClick()
    fileUploadInput.selectFile(file)
    confirmUploadMetadataButton.doClick()
    uploadSuccessMessage.awaitVisible()
    xOut()
  }

}

