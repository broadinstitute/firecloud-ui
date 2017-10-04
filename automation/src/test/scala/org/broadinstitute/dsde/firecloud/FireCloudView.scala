package org.broadinstitute.dsde.firecloud

import org.broadinstitute.dsde.firecloud.test.WebBrowserUtil
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.WebBrowser

/**
  * Parent class for all pages and components.
  */
abstract class FireCloudView(implicit webDriver: WebDriver)
  extends WebBrowser with WebBrowserUtil {
  def awaitReady(): Unit

  /** Query for the FireCloud Spinner component */
  val spinner: CssSelectorQuery = testId("spinner")

  def readText(q: Query): String = {
    find(q) map { _.text } getOrElse ""
  }
}
