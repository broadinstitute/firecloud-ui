package org.broadinstitute.dsde.firecloud.component

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium.WebDriver
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
    // not using await condition (isVisible && isEnabled) because want to see WebElement query string in log
    new WebDriverWait(webDriver, 30).until(ExpectedConditions.elementToBeClickable(query.element.underlying))
    scrollToVisible()
    click on query
  }
}
