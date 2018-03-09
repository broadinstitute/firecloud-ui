package org.broadinstitute.dsde.firecloud

import org.broadinstitute.dsde.workbench.service.test.{WebBrowserUtil, Awaiter}
import org.openqa.selenium.WebDriver
import org.scalatest.concurrent.Eventually
import org.scalatest.selenium.WebBrowser
import scala.util.Random


/**
  * Parent class for all pages and components.
  */
abstract class FireCloudView(implicit webDriver: WebDriver)
  extends Awaiter with WebBrowser with WebBrowserUtil with Eventually {

  def readText(q: Query): String = {
    find(q) map { _.text } getOrElse ""
  }

  def readAllText(q: Query): List[String] = {
    val allText = findAll(q) map { _.text }
    allText.toList
  }

}
