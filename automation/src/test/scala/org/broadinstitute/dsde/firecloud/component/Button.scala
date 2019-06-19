package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.WebDriver

/**
  * Component modeling buttons.cljs (both Button and SidebarButton).
  * Not meant to model any arbitrary clickable thing.
  *
  * @param queryString the QueryString object representing the root element of the component
  */
case class Button(queryString: QueryString)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful with Clickable {

  private val enabledState = "enabled"
  private val disabledState = "disabled"

  def isStateEnabled: Boolean = { awaitVisible(); getState == enabledState }
  def isStateDisabled: Boolean = { awaitVisible(); getState == disabledState }

  def awaitEnabledState(): Unit = awaitState(enabledState)
}