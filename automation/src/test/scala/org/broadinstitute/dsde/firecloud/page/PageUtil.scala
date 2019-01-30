package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.fixture.WebDriverIdLogging
import org.openqa.selenium.{TimeoutException, WebDriver}
import org.scalatest.selenium.Page

import scala.util.{Failure, Success, Try}

/**
  * Mix-in utilities for ScalaTest's Page.
  */
trait PageUtil[P <: Page] extends FireCloudView with WebDriverIdLogging { self: P =>

  /**
    * Sends the browser to the URL for this Page object. Returns the page
    * object to facilitate call chaining.
    *
    * @param webDriver implicit WebDriver for the WebDriverWait
    * @return the Page object
    */
  def open(implicit webDriver: WebDriver): P = {
    go to this
    Try (await notVisible cssSelector("[data-test-id=spinner]")) match {
      case Success(_) =>
      case Failure(_) =>
        log.warn(s"Timed out waiting for Spinner stop on page $url. Opened url is ${webDriver.getCurrentUrl}. Retry with page reload.")
        go to this
        webDriver.navigate().refresh()
        try { await notVisible cssSelector("[data-test-id=spinner]") } catch {
          case e: TimeoutException =>
            throw new TimeoutException(s"Timed out waiting for Spinner stop on page $url. Opened url is ${webDriver.getCurrentUrl}. Test stopping...", e)
        } // will rethrow timeout exception if spinner still isn't going away in retry to abort test
    }
    Try (awaitReady()) match {
      case Success(_) =>
      case Failure(f) =>
        log.error(s"Timed out ($defaultTimeOutInSeconds seconds) waiting for page ${this.url}")
        throw(f)
    }

    this
  }
}
