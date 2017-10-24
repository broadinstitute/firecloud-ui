package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.WebDriver

/**
  * Component modeling buttons.cljs (both Button and SidebarButton).
  * Not meant to model any arbitrary clickable thing.
  *
  * @param queryString the QueryString object representing the root element of the component
  * @param webDriver webdriver
  */
case class Button(queryString: QueryString)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful with Clickable {

  def isStateEnabled: Boolean = getState == "enabled"
  def isStateDisabled: Boolean = getState == "disabled"
}
