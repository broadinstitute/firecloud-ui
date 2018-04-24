package org.broadinstitute.dsde.firecloud.component

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.{StaleElementReferenceException, TimeoutException, WebDriver, WebDriverException}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

/**
  * Mix in for Components which can be clicked to do something arbitrary (such as navigate
  * somewhere, open a modal, etc.). Should not be used for self-contained widgets like
  * checkboxes that represent their own state
  */
trait Clickable extends LazyLogging { this: Component =>
  /**
    * Click on the element modeled by this Component
    */
  def doClick()(implicit webDriver: WebDriver): Unit = {
    try {
      await condition (isVisible && isEnabled)
    } catch {
      case e: TimeoutException =>
        // show me query string on failed WebElement
        new WebDriverWait(webDriver, 5).until(ExpectedConditions.elementToBeClickable(query.element.underlying))
    }
    scrollToVisible()
    try {
      click on query
    } catch {
      // on rare occasion, stale exception happens on click
      case e: StaleElementReferenceException => click on query
      case e: WebDriverException =>
        logger.warn("doClick: " + e.getMessage)
        // make an attempt to recover when this exact error occurred
        if (e.getMessage.contains("Other element would receive the click")) {
          logger.warn("retrying \"click on query\" after sleep 5 seconds")
          Thread.sleep(5000)
          click on query
        }
    } finally {
      Thread sleep 100
      await notVisible cssSelector("[data-test-id=spinner]")
    }
  }
}
