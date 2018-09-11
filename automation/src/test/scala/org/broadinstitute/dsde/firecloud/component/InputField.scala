package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

trait InputField {
  def setText(text: String): Unit
  def getText: String
}

case class TextField(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) with InputField {
  def setText(text: String): Unit = {
    textField(query).value = text
  }

  def getText: String = {
    textField(query).value
  }
}

case class NumberField(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) with InputField {
  def setText(text: String): Unit = {
    numberField(query).value = text
  }

  def getText: String = {
    numberField(query).value
  }
}