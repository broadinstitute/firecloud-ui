package org.broadinstitute.dsde.firecloud.component

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{StaleElementReferenceException, TimeoutException, WebDriver, WebDriverException}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

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
        logger.warn(s"Encountered StaleElementReferenceException. Click again on: $query")
        Thread.sleep(2000)
        click on query
      case e: WebDriverException =>
        logger.debug(e.getMessage)
        // make an attempt to recover for this exact error


        Try {
          if (e.getMessage.contains("Other element would receive the click")) {
            logger.warn(s"Encountered WebDriverException. Click again on: $query")
            Thread.sleep(2000)
            click on query
          }
        }.recover {
          /* https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/2766
            ChromeDriver always clicks the middle of the Link element. Sometimes, the link is not clickable in its middle.
            To handle this: Use the advanced user interactions API to click link at offset position.
          */
          case e: WebDriverException =>
            if (query.findElement.get.underlying.getTagName == "a") {
              val action = new Actions(webDriver)
              action.moveToElement(query.findElement.get.underlying, 10, 0).click().perform()
            }
          case ex: Exception => throw ex
         }
    }
  }

}
