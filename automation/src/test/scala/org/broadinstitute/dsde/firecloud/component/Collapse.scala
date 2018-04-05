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
    await visible toggleComponent
    click on toggleComponent
    awaitReady()
  }

  def ensureCollapsed(): Unit = {
    await condition {
      if (isExpanded) toggle()
      stateOf(toggleComponent) == "collapsed"
    }
  }

  def ensureExpanded(): Unit = {
    await condition {
      if (!isExpanded) toggle()
      stateOf(toggleComponent) == "expanded"
    }
  }

  def getInner: A = {
    ensureExpanded()
    inner
  }
}
