package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Select2(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  private val renderedClass = "select2-selection__rendered"
  private val listItemClass = "select2-results__option"

  def select(option: String): Unit = {
    click on find(className(renderedClass)).get
    pressKeys(option)
    click on find(className(listItemClass)).get
  }
}
