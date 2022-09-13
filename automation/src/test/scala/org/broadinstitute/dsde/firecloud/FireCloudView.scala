package org.broadinstitute.dsde.firecloud

import org.broadinstitute.dsde.workbench.service.test.{Awaiter, WebBrowserUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.WebBrowser

/**
  * Parent class for all pages and components.
  */
abstract class FireCloudView(implicit webDriver: WebDriver)
  extends Awaiter with WebBrowser with WebBrowserUtil {

  def readText(q: Query): String = {
    find(q) map { _.text } getOrElse ""
  }

  def readAllText(q: Query): List[String] = {
    val allText = findAll(q) map { _.text }
    allText.toList
  }

}
