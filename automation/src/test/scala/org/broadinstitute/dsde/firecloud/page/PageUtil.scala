package org.broadinstitute.dsde.firecloud.page

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.fixture.WebDriverIdLogging
import org.openqa.selenium.WebDriver
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
        log.warn(s"Refreshing page ${webDriver.getCurrentUrl}")
        go to this
        await notVisible cssSelector("[data-test-id=spinner]") // will throw timeout exception if spinner still isn't going away in retry
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
