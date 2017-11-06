package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class FileSelector(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  def selectFile(filename: String): Unit = {
    executeScript("var field = document.getElementsByName('entities'); field[0].style.display = '';")
    val webElement = find(query).get.underlying
    webElement.clear()
    webElement.sendKeys(filename)
  }
}
