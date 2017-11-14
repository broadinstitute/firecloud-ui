package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class TextArea(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def setText(text: String): Unit = {
    textArea(query).value = text
  }

  def getText: String = {
    textArea(query).value
  }
}
