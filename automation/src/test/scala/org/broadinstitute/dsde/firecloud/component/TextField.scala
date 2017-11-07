package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class TextField(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def setText(text: String): Unit = {
    textField(query).value = text
  }

  def getText: String = {
    textField(query).value
  }
}
