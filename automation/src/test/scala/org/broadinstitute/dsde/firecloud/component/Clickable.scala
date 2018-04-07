package org.broadinstitute.dsde.firecloud.component

import com.typesafe.scalalogging.LazyLogging
import org.openqa.selenium
import org.openqa.selenium.{WebDriver, WebDriverException}
import org.openqa.selenium.interactions.Actions

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
    await condition (isVisible && isEnabled)
    scrollToVisible()
    Try {
      click on query
    }.recover {
      case _: WebDriverException => // retry
        val size: selenium.Dimension = query.element.underlying.getSize()
        // moveToElement scrolls WebElement into viewport if it was outside viewport
        new Actions(webDriver).moveToElement(query.element.underlying, -(size.width * 50)/100, 0).build.perform()
        click on query
    }
  }
}
