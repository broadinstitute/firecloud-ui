package org.broadinstitute.dsde.firecloud.page.workspaces

import java.io.File
import java.text.SimpleDateFormat

import org.broadinstitute.dsde.firecloud.component.{Button, FileSelector, Label, Table}
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.workbench.service.util.Util
import org.openqa.selenium.WebDriver
import org.scalatest.concurrent.Eventually
import org.scalatest.selenium.Page
import org.scalatest.time.{Seconds, Span}


class WorkspaceDataPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceDataPage] with Eventually {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/data"

  implicit override val patienceConfig = {
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(1, Seconds)))
  }

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
  def downloadMetadata(downloadPath: Option[String] = None): Option[String] = synchronized {

    def archiveDownloadedFile(sourcePath: String): String = {
      // wait up to 10 seconds for file exist
      val f = new File(sourcePath)
      eventually {
        assert(f.exists(), s"Timed out (10 seconds) waiting for file $f")
      }
      val date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new java.util.Date())
      val destFile = new File(sourcePath).getName + s".$date"
      val destPath = s"downloads/$destFile"
      Util.moveFile(sourcePath, destPath)
      logger.info(s"Moved file. sourcePath: $sourcePath, destPath: $destPath")
      destPath
    }

    downloadMetadataButton.awaitVisible()

    /*
     * Downloading a file will open another window while the download is in progress and
     * automatically close it when the download is complete.
     */
    // await condition (windowHandles.size == 1, 30)
    // .submit call takes care waiting for a new window
    logger.info(s"form: ${form.queryString}")
    find(form).get.underlying.submit()

    for {
      path <- downloadPath
      entityType <- find(CssSelectorQuery(downloadMetadataButton.query.queryString)).get.attribute("data-entity-type")
    } yield archiveDownloadedFile(s"$path/$entityType.txt")
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

  /**
    * Imports metadata from a file.
    */
  def importFile(file: String): Unit = {
    importFromFileButton.doClick()
    fileUploadInput.selectFile(file)

    confirmUploadMetadataButton.awaitEnabledState()
    confirmUploadMetadataButton.doClick()
    confirmUploadMetadataButton.awaitNotVisible()

    chooseFileButton.awaitEnabledState()
    uploadSuccessMessage.awaitVisible()

    xOut()
  }

}

