package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class WDLField(id: String)(implicit webDriver: WebDriver) extends Component(CSSQuery(s"[data-test-id='$id'] .CodeMirror")) {
  def fillWDL(wdl: String): Unit = {
    val sanitized = wdl.replaceAll("\"" ,"\\\\\"").replaceAll("\n", "\\\\n")
    await visible (query, 5) // needed because query.webElement is used next
    executeScript("arguments[0].CodeMirror.setValue(\"" + sanitized + "\");", query.webElement)
  }
}
