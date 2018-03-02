package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.{FireCloudView, Stateful}
import org.openqa.selenium.WebDriver

case class Collapse[A <: FireCloudView](queryString: QueryString, private val inner: A)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful {
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
    if (!isExpanded) {
      click on toggleComponent
      await condition isExpanded
    }
  }

  def ensureCollapsed(): Unit = {
    if (isExpanded) {
      click on toggleComponent
      await condition stateOf(toggleComponent) == "collapsed"
    }
  }

  def getInner: A = {
    ensureExpanded()
    inner
  }
}
