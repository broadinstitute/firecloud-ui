package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class SearchField(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def setText(text: String): Unit = {
    searchField(element).value = text
  }

  def getText: String = {
    searchField(element).value
  }
}
