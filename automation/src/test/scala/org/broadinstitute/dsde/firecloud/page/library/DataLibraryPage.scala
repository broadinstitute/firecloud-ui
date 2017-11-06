package org.broadinstitute.dsde.firecloud.page.library

import org.broadinstitute.dsde.firecloud.component.Link
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Data Library page.
  */
class DataLibraryPage(implicit webDriver: WebDriver) extends BaseFireCloudPage with Page with PageUtil[DataLibraryPage] {
  override val url: String = s"${Config.FireCloud.baseUrl}#library"

  override def awaitReady(): Unit = {
    await text "Data Use Limitation"
  }

  def validateLocation(): Unit = {
    // TODO: Use something more reliable to validate that the browser is on the right page.
    await text "Matching Cohorts"
  }

  def hasDataset(name: String): Boolean = {
    // TODO: need to search the table to get here
    Link(s"dataset-$name").isVisible
  }
}
