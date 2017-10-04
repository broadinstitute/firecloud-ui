package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class Checkbox(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def isChecked: Boolean = checkbox(element).isSelected

  def ensureChecked(): Unit = {
    if (!isChecked)
      click on element
  }

  def ensureUnchecked(): Unit = {
    if (isChecked)
      click on element
  }
}
