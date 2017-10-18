package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Checkbox(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def isChecked: Boolean = checkbox(element).isSelected

  def ensureChecked(): Unit = {
    checkbox(element).select()
  }

  def ensureUnchecked(): Unit = {
    checkbox(element).clear()
  }
}
