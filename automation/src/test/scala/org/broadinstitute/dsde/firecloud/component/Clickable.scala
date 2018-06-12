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
      await condition ( isVisible && isEnabled && invisibleSpinner)
    } catch {
      case e: TimeoutException =>
        // show me query string on failed WebElement
        new WebDriverWait(webDriver, 1).until(ExpectedConditions.elementToBeClickable(query.element.underlying))
    }
    scrollToVisible()
    try {
      click on query
    } catch {
      // on rare occasion, encounters stale exception
      case e: StaleElementReferenceException =>
        logger.warn(s"Encountered StaleElementReferenceException. Retrying click on query: $query")
        click on query
      case e: WebDriverException =>
        logger.debug(e.getMessage)
        // make an attempt to recover for this exact error
        if (e.getMessage.contains("Other element would receive the click")) {
          Thread.sleep(5000)
          logger.warn(s"Encountered WebDriverException. Retrying click on query: $query")
          click on query
        }
    }
  }

}
