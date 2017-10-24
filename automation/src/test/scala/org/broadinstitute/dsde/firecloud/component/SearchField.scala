package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class SearchField(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def setText(text: String): Unit = {
    searchField(query).value = text
  }

  def getText: String = {
    searchField(query).value
  }
}
