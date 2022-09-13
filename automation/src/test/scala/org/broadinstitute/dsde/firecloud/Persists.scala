package org.broadinstitute.dsde.firecloud

import org.openqa.selenium.{JavascriptExecutor, WebDriver}

trait Persists { this: FireCloudView =>
  val query: Query

  def getPersistenceKey(implicit webDriver: WebDriver): String = {
    query.element.attribute("data-test-persistence-key").get
  }

  def getStoredValue(implicit webDriver: WebDriver): String = {
    val js = webDriver.asInstanceOf[JavascriptExecutor]
    js.executeScript(s"return window.localStorage.getItem('$getPersistenceKey')").asInstanceOf[String]
  }

  def putStoredValue(value: String)(implicit webDriver: WebDriver): Unit = {
    val js = webDriver.asInstanceOf[JavascriptExecutor]
    js.executeScript(s"window.localStorage.setItem('$getPersistenceKey', '$value')")
  }
}
