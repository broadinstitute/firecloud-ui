package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Label(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def getText: String = readText(element)
}
