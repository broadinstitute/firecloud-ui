package org.broadinstitute.dsde.firecloud.pages

import org.broadinstitute.dsde.firecloud.{Config, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Page class for the Data Library page.
  */
class DataLibraryPage(implicit webDriver: WebDriver) extends FireCloudView with Page with PageUtil[DataLibraryPage] {
  override val url: String = s"${Config.FireCloud.baseUrl}#org.broadinstitute.dsde.firecloud.library"

  def validateLocation(): Unit = {
    // TODO: Use something more reliable to validate that the browser is on the right page.
    await text "Matching Cohorts"
  }
}
