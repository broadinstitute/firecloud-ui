package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

/**
  * Mix in for Components which can be clicked to do something arbitrary (such as navigate
  * somewhere, open a modal, etc.). Should not be used for self-contained widgets like
  * checkboxes that don't really
  */
trait Clickable { this: Component =>
  /**
    * Click on the element modeled by this Component
    */
  def doClick()(implicit webDriver: WebDriver): Unit = click on query
}
