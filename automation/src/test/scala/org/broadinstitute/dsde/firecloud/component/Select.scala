package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Select(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def select(o: String): Unit = singleSel(query).value = option value o
}
