package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class WDLField(id: String)(implicit webDriver: WebDriver) extends Component(s"[data-test-id='$id'] .CodeMirror", raw = true) {
  def fillWDL(wdl: String): Unit = {
    val sanitized = wdl.replaceAll("\"" ,"\\\\\"").replaceAll("\n", "\\\\n")
    executeScript("arguments[0].CodeMirror.setValue(\"" + sanitized + "\");", element.webElement)
  }
}
