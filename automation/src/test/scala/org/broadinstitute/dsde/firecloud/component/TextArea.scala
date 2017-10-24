package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class TextArea(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def setText(text: String): Unit = {
    textArea(query).value = text
  }

  def getText: String = {
    textArea(query).value
  }
}
