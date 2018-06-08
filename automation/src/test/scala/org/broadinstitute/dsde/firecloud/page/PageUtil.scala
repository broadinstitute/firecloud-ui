package org.broadinstitute.dsde.firecloud.page

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudView
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

import scala.util.{Failure, Success, Try}

/**
  * Mix-in utilities for ScalaTest's Page.
  */
trait PageUtil[P <: Page] extends FireCloudView with LazyLogging { self: P =>

  /**
    * Sends the browser to the URL for this Page object. Returns the page
    * object to facilitate call chaining.
    *
    * @param webDriver implicit WebDriver for the WebDriverWait
    * @return the Page object
    */
  def open(implicit webDriver: WebDriver): P = {
    go to this
    val result = Try (await notVisible cssSelector("[data-test-id=spinner]"))
    result match {
      case Success(_) =>
      case Failure(_) =>
        logger.warn(s"Timed out (60 seconds) waiting for spinner to disappear on page. Refreshing page ${this.url}")
        webDriver.navigate().refresh() // reload page
        await notVisible cssSelector("[data-test-id=spinner]") // test terminates if spinner is not going away in retry
    }
    Try (awaitReady()) match {
      case Success(_) =>
      case Failure(_) =>
        logger.warn(s"Timed out ($defaultTimeOutInSeconds seconds) waiting for page ready. Reopening page ${this.url}")
        go to this
        await notVisible cssSelector("[data-test-id=spinner]")
        awaitReady() // test terminates if timeout exception happens again
    }

    logger.info(s"Opened page ${this.url}")
    this
  }
}
