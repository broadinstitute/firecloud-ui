package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class FileSelector(id: String)(implicit webDriver: WebDriver) extends Component(id) {
  def selectFile(filename: String): Unit = {
    executeScript("var field = document.getElementsByName('entities'); field[0].style.display = '';")
    val webElement = find(element).get.underlying
    webElement.clear()
    webElement.sendKeys(filename)
  }
}
