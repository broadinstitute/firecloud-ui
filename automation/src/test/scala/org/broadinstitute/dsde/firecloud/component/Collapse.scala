package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.WebBrowser

case class Collapse[A <: WebBrowser](private val id: String, private val inner: A)(implicit webDriver: WebDriver)
  extends Component(id) with Stateful {
  private val toggleComponent = findInner("toggle")

  def isExpanded: Boolean = {
    awaitEnabled()
    stateOf(toggleComponent) == "expanded"
  }

  def toggle(): Unit = {
    awaitEnabled()
    click on toggleComponent
  }

  def ensureExpanded(): Unit = {
    if (!isExpanded)
      click on toggleComponent
  }

  def ensureCollapsed(): Unit = {
    if (isExpanded)
      click on toggleComponent
  }

  def getInner: A = {
    ensureExpanded()
    inner
  }
}
