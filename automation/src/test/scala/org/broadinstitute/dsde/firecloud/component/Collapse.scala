package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.{FireCloudView, Stateful}
import org.openqa.selenium.WebDriver

case class Collapse[A <: FireCloudView](queryString: QueryString, private val inner: A)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful {

  private val toggleComponent = new Link("toggle" inside this) with Stateful

  override def awaitReady(): Unit = inner.awaitReady()

  def isExpanded: Boolean = {
    await condition { toggleComponent.isVisible && toggleComponent.isEnabled }
    toggleComponent.getState == "expanded"
  }

  def toggle(): Unit = {
    val state = isExpanded
    toggleComponent.doClick()
    // compare data-test-state after click
    await condition(state != isExpanded, 15)
  }

  def ensureCollapsed(): Unit = {
    await condition {
      if (isExpanded) toggle()
      toggleComponent.getState == "collapsed"
    }
  }

  def ensureExpanded(): Unit = {
    await condition {
      if (!isExpanded) toggle()
      toggleComponent.getState == "expanded"
    }
  }

  def getInner: A = {
    ensureExpanded()
    awaitReady()
    inner
  }
}
