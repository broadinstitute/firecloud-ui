package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Link(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def doClick(): Unit = click on element
}
