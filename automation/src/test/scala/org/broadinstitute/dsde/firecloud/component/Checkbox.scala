package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Checkbox(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def isChecked: Boolean = checkbox(query).isSelected

  def ensureChecked(): Unit = {
    checkbox(query).select()
  }

  def ensureUnchecked(): Unit = {
    checkbox(query).clear()
  }
}
