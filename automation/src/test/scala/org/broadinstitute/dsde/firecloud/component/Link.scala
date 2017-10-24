package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Link(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def doClick(): Unit = click on query
}
