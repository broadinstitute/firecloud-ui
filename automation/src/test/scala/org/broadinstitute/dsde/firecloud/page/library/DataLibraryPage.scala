package org.broadinstitute.dsde.firecloud.page.library

import org.broadinstitute.dsde.firecloud.component.{Link, SearchField, Table}
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
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
  def doSearch(searchParameter: String): Unit = {
    searchField.setText(searchParameter)
    pressKeys("\n")
    LibraryTable.awaitReady()

  }
}
