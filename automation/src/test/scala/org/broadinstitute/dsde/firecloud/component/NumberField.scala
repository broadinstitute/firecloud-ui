package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class NumberField(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def setText(number: Int): Unit = {
    numberField(query).value = number.toString
  }

  def getText: Int = {
    numberField(query).value.toInt
  }
}
