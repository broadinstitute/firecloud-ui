package org.broadinstitute.dsde.firecloud.component

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.{StaleElementReferenceException, WebDriver, WebElement}

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
    await condition {
      isVisible && isEnabled
    }
    isPageStale() // if exception thrown, page was not ready, click probably fail
    scrollToVisible()
    click on query
  }

  // DEBUG click issue
  private def isPageStale()(implicit webDriver: WebDriver): Unit = {
    await condition {
      try {
        val elem = find(CssSelectorQuery("#app")).get
        elem.attribute("innerHTML").nonEmpty
      } catch {
        case e: StaleElementReferenceException =>
          logger.error("Stale WebElement #app")
          false
        case e: Exception =>
          logger.error("Unknown Exception while checking #app ", e)
          false
      }
    }
  }

}