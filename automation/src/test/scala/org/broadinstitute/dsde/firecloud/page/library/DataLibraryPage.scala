package org.broadinstitute.dsde.firecloud.page.library

import java.util

import org.broadinstitute.dsde.firecloud.component.{Link, SearchField, Table, Tags}
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.broadinstitute.dsde.firecloud.util.Retry.retry
import org.openqa.selenium.{By, WebDriver, WebElement}
import org.scalatest.selenium.Page

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationLong

/**
  * Page class for the Data Library page.
  */
class DataLibraryPage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[DataLibraryPage] {
  override val url: String = s"${Config.FireCloud.baseUrl}#library"

  private val LibraryTable = Table("library-table")
  private val searchField = SearchField("library-search-input")
  private val tags = Tags("tags")
  private val consentCodes = Tags("consent-codes")

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

  def waitForDataset(name:String) = {
    retry[DataLibraryPage](10.seconds, 5.minutes)({
      val libraryPage = open
      if (libraryPage.hasDataset(name))
        Some(libraryPage)
      else None
    })
  }

  def doSearch(searchParameter: String): Unit = {
    searchField.setText(searchParameter)
    pressKeys("\n")
    LibraryTable.awaitReady()
  }

  def getConsentCodes(): List[String] = {
    consentCodes.awaitVisible()
    consentCodes.getTags
  }

  def getTags(): List[String] = {
    tags.awaitVisible()
    tags.getTags
  }
}
