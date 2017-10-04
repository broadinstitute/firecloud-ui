package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

/**
  * Mix-in utilities for ScalaTest's Page.
  */
trait PageUtil[P <: Page] extends FireCloudView { self: P =>

  /**
    * Sends the browser to the URL for this Page object. Returns the page
    * object to facilitate call chaining.
    *
    * @param webDriver implicit WebDriver for the WebDriverWait
    * @return the Page object
    */
  def open(implicit webDriver: WebDriver): P = {
    go to this
    awaitReady()
    this
  }
}
