package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class EmailField(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def setText(text: String): Unit = {
    emailField(element).value = text
  }

  def getText: String = {
    emailField(element).value
  }
}
