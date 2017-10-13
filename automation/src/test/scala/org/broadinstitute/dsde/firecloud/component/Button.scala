package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.WebDriver

case class Button(id: String)(implicit webDriver: WebDriver) extends Component(id) with Stateful {
  def isStateEnabled: Boolean = getState == "enabled"
  def isStateDisabled: Boolean = getState == "disabled"

  def doClick(): Unit = click on element
}
