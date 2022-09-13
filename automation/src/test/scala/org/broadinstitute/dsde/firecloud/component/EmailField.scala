package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class EmailField(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def setText(text: String): Unit = {
    emailField(query).value = text
  }

  def getText: String = {
    emailField(query).value
  }
}
