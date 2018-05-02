package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.LocalFileDetector

case class FileSelector(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {

  def selectFile(filename: String): Unit = {
    val fileDetector = new LocalFileDetector()
    val file = fileDetector.getLocalFile(filename)
    val webElement = find(query).get.underlying
    webElement.sendKeys(file.getAbsolutePath)
  }

  /**
    * This is the original selectFile() that executes javaScript to load file.
    * Keep it for now until for sure don't need
    * @param filename
    */
  @deprecated("","")
  def selectFileJs(filename: String): Unit = {
    executeScript("var field = document.getElementsByName('entities'); field[0].style.display = '';")
    val webElement = find(query).get.underlying
    webElement.clear()
    webElement.sendKeys(filename)
  }

}
