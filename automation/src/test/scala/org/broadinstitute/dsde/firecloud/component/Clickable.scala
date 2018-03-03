package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

/**
  * Mix in for Components which can be clicked to do something arbitrary (such as navigate
  * somewhere, open a modal, etc.). Should not be used for self-contained widgets like
  * checkboxes that represent their own state
  */
trait Clickable { this: Component =>
  /**
    * Click on the element modeled by this Component
    */
  def doClick()(implicit webDriver: WebDriver): Unit = {
    // before click, wait up to 10 seconds for element become clickable
    new WebDriverWait(webDriver, 10).until(
      ExpectedConditions.elementToBeClickable(query.by))
    scrollToVisible()
    click on query
  }
}
