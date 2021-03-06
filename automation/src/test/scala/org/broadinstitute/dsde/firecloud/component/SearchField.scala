package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class SearchField(queryString: QueryString)(implicit webDriver: WebDriver)
  extends Component(queryString) with Suggests {

  def setText(text: String): Unit = {
    searchField(query).value = text
  }

  def getText: String = {
    searchField(query).value
  }

  def getAttribute(name: String): Option[String] = {
    searchField(query).attribute(name)
  }

  def clear: Unit = {
    searchField(query).clear()
  }

}
