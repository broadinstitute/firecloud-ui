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
    // await condition (isVisible && isEnabled)
    waitUntilTimeout(ExpectedConditions.elementToBeClickable(query.element.underlying))(_,_)
    scrollToVisible()
    try {
      click on query
    } catch {
      // No retry when:
      // 1) WebElement not found
      // 2)
      case e: WebDriverException => // retry
        logger.warn(e.getMessage + s"retry clicking $query")
        val size: selenium.Dimension = query.element.underlying.getSize()
        // moveToElement scrolls WebElement into viewport if it was outside viewport
        new Actions(webDriver).moveToElement(query.element.underlying, -(size.width * 25)/100, 0).click.build.perform()
    }
  }

  // helper
  def waitUntilTimeout[T](cond: => T)(timeout: Int = 30, msg: String)(implicit webDriver: WebDriver): T = {

    val wait = new WebDriverWait(webDriver, timeout)
    val func = new Function[WebDriver, T]() {
      override def apply(driver: WebDriver): T = {
        cond
      }
    }
    wait.withMessage(msg).until(func)
  }
}
