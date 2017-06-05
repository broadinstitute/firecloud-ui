package org.broadinstitute.dsde.firecloud.pages

import org.broadinstitute.dsde.firecloud.{Config, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Created by ansingh on 5/22/17.
  */
class WorkspaceDataPage(namespace: String, name: String)(implicit webDriver: WebDriver) extends WorkspacePage with Page with PageUtil[WorkspaceDataPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/data"

  def importFile(file: String) = {
    val importModal = gestures.clickImportMetadataButton()
    importModal.importFile(file)
  }

  object gestures {

    private val importMetadataButtonQuery = testId("import-metadata-button")

    def clickImportMetadataButton(): ImportMetadataModal = {
      click on (await enabled importMetadataButtonQuery)
      new ImportMetadataModal
    }
  }

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
    gestures.clickImportFromAnotherFileButton()
    await toggle spinner
    gestures.clickChooseFileButton() //???
  }

  object gestures {

    private val importFromFileButtonQuery: Query = testId("import-from-file-button")
    private val copyFromAnotherWorkspaceButtonQuery: Query = testId("copy-from-another-workspace-button")
    private val chooseFileButton: Query = testId("choose-file-button")

    def clickImportFromAnotherFileButton(): Unit = {
      click on (await enabled importFromFileButtonQuery)
    }

    def clickCopyFromAnotherWorkspaceButton(): Unit = {
      click on (await enabled copyFromAnotherWorkspaceButtonQuery)
    }

    def clickChooseFileButton(): Unit = {
      click on (await enabled chooseFileButton)
    }
  }

}

