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
}