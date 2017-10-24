package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Select(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def select(o: String): Unit = singleSel(query).value = option value o
}
