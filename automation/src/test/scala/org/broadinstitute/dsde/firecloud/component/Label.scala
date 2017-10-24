package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Label(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def getText: String = readText(query)
}
