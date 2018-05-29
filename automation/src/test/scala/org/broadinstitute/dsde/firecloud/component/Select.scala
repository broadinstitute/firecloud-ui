package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

case class Select(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def select(o: String): Unit = {
    new WebDriverWait(webDriver, 30).until(ExpectedConditions.elementToBeClickable(query.element.underlying))
    singleSel(query).value = option value o
  }
}
