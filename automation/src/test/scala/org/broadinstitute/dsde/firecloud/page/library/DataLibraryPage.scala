package org.broadinstitute.dsde.firecloud.page.library

import org.broadinstitute.dsde.firecloud.component.{Link, SearchField, Table}
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, OKCancelModal, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Data Library page.
  */
class DataLibraryPage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[DataLibraryPage] {
  override val url: String = s"${Config.FireCloud.baseUrl}#library"

  private val LibraryTable = Table("library-table")
  private val searchField = SearchField("library-search-input")

  override def awaitReady: Unit = {
    LibraryTable.awaitReady()
  }

  def validateLocation(): Unit = {
    LibraryTable.awaitReady()
  }

  def hasDataset(name: String): Boolean = {
    doSearch(name)
    Link(s"dataset-$name").isVisible
  }


  def openDataset(name: String): Unit = {
    Link(s"dataset-$name").doClick()
  }

  def doSearch(searchParameter: String): Unit = {
    searchField.setText(searchParameter)
    pressKeys("\n")
    LibraryTable.awaitReady()

  }


  case class RequestAccessModal(implicit webDriver: WebDriver) extends OKCancelModal {
    val tcgaAccessText = "For access to TCGA controlled data please apply for access via dbGaP"
    def validateLocation: Boolean = {
      testId("push-message").element != null
    }
    def readMessageModalText: String = {
      readText(testId("request-access-text"))
    }
  }




}


