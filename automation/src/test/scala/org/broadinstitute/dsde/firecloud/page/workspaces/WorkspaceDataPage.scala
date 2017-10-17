package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.components.Table
import org.broadinstitute.dsde.firecloud.page.{FireCloudView, PageUtil}
import org.openqa.selenium.{Keys, WebDriver}
import org.openqa.selenium.interactions.Actions
import org.scalatest.selenium.Page


class WorkspaceDataPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceDataPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/data"

  def importFile(file: String) = {
    val importModal = ui.clickImportMetadataButton()
    importModal.importFile(file)
  }

  def getNumberOfParticipants(): Int = {
    ui.getNumberOfParticipants()
  }

  override def awaitLoaded(): WorkspaceDataPage = {
    await condition ui.hasimportMetadataButton()
    this
  }

  def hideColumn(header: String) = {
    if (ui.readColumnHeaders.contains(header)) {
      ui.showColumnEditor()
      ui.toggleColumnVisibility(header)
      ui.hideColumnEditor()
    }
  }

  trait UI extends super.UI {
    private val dataTable = Table("entity-table")

    private val importMetadataButtonQuery = testId("import-metadata-button")
    private val columnEditorButtonQuery= testId("columns-button")
    private val columnHeaderQuery: CssSelectorQuery = testId("column-header")
    private val filterField = testId("filter-input")

    def clearFilterField() = {
      await enabled filterField
      searchField(filterField).value = ""
      pressKeys("\n")
    }
    def hasimportMetadataButton(): Boolean = {
      find(importMetadataButtonQuery).isDefined
    }

    def clickImportMetadataButton(): ImportMetadataModal = {
      click on importMetadataButtonQuery
      new ImportMetadataModal
    }

    def getNumberOfParticipants(): Int = {
      // TODO: click on the tab and read the actual table size
      dataTable.readDisplayedTabCount("participant")
    }

    def hideColumnEditor() = {
      val action = new Actions(webDriver)
      action.sendKeys(Keys.ESCAPE)
    }

    def showColumnEditor() = {
      click on columnEditorButtonQuery
    }

    def toggleColumnVisibility(header: String) = {
      click on testId(s"$header-column-toggle")
    }

    def readColumnHeaders: List[String] = {
      await notVisible spinner
      readAllText(columnHeaderQuery)
    }

  }
  object ui extends UI
}

/**
  * Page class for the import data modal.
  */
class ImportMetadataModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    * Confirms the request to delete a workspace. Returns after the FireCloud
    * busy spinner disappears.
    */
  def importFile(file: String): Unit = {
    ui.clickImportFromFileButton()
    ui.uploadData(file)
    ui.clickUploadMetaData()
    assert(ui.isUploadSuccessMessagePresent)
    ui.clickXButton()
  }

  object ui {

    private val importFromFileButtonQuery: Query = testId("import-from-file-button")
    private val copyFromAnotherWorkspaceButtonQuery: Query = testId("copy-from-another-workspace-button")
    private val chooseFileButton: Query = testId("choose-file-button")
    private val fileUploadInputQuery: Query = testId("data-upload-input")
    private val fileUploadContainerQuery: Query = testId("data-upload-container")
    private val confirmUploadMetadataButtonQuery: Query = testId("confirm-upload-metadata-button")
    private val xButtonQuery: Query = testId("x-button")
    private val uploadSuccessMessageQuery = testId("upload-success-message")


    def clickXButton() = {
      click on (await enabled xButtonQuery)
    }

    def clickImportFromFileButton() = {
      click on importFromFileButtonQuery
    }

    def clickCopyFromAnotherWorkspaceButton() = {
      click on (await enabled copyFromAnotherWorkspaceButtonQuery)
    }

    def clickChooseFileButton() = {
      click on (await enabled chooseFileButton)
    }

    def uploadData(filePath: String) = {
      executeScript("var field = document.getElementsByName('entities'); field[0].style.display = '';")
      val webElement = find(fileUploadInputQuery).get.underlying
      webElement.clear()
      webElement.sendKeys(filePath)
    }

    def clickUploadMetaData() = {
      click on (await enabled confirmUploadMetadataButtonQuery)
    }

    def isUploadSuccessMessagePresent(): Boolean = {
      await enabled uploadSuccessMessageQuery
      // this seems like a terrible way to do this
      find(uploadSuccessMessageQuery).size == 1
    }
  }
}

