package org.broadinstitute.dsde.firecloud.component

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium
import org.openqa.selenium.{NoSuchElementException, StaleElementReferenceException, WebDriver, WebDriverException}
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.{ExpectedConditions, FluentWait, Wait, WebDriverWait}
import com.google.common.base.Function

import scala.concurrent.TimeoutException
import scala.util.Try

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
    // want to see WebElement query string so not using await condition (isVisible && isEnabled)
    new WebDriverWait(webDriver, 30).until(ExpectedConditions.elementToBeClickable(query.element.underlying))
    scrollToVisible() // moves WebElement to top of page. needs a helper method to verify location is fixed
    Thread.sleep(500) // temp solution to wait page stop "moving" after scrollToVisible
    try {
      click on query
    } catch {
      // No retry when:
      // 1) WebElement not found
      // 2) ..
      case e: WebDriverException => // retry
        logger.warn(e.getMessage + s" Retry click on $query")
        val size: selenium.Dimension = query.element.underlying.getSize()
        // moveToElement scrolls WebElement into viewport if it was outside viewport
        new Actions(webDriver).moveToElement(query.element.underlying, -(size.width * 25)/100, 0).click.build.perform()
    }
  }
}
