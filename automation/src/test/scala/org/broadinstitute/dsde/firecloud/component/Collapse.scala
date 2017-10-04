package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.{FireCloudView, Stateful}
import org.openqa.selenium.WebDriver

case class Collapse[A <: FireCloudView](private val id: String, private val inner: A)(implicit webDriver: WebDriver)
  extends Component(id) with Stateful {
  private val toggleComponent = findInner("toggle")

  override def awaitReady(): Unit = inner.awaitReady()

  def isExpanded: Boolean = {
    awaitVisible()
    stateOf(toggleComponent) == "expanded"
  }

  def toggle(): Unit = {
    awaitVisible()
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
