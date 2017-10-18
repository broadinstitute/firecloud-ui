package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class TextField(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def setText(text: String): Unit = {
    textField(element).value = text
  }

  def getText: String = {
    textField(element).value
  }
}
