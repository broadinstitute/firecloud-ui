package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.{FireCloudView, Stateful}
import org.openqa.selenium.WebDriver

case class Collapse[A <: FireCloudView](queryString: QueryString, private val inner: A)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful {

  private val toggleCssQuery: CssSelectorQuery = findInner("toggle")
  private val toggleLink = Link(CSSQuery(toggleCssQuery.queryString))

  override def awaitReady(): Unit = inner.awaitReady()

  def isExpanded: Boolean = {
    await condition { toggleLink.isVisible && toggleLink.isEnabled }
    stateOf(toggleCssQuery) == "expanded"
  }

  def toggle(): Unit = {
    val state = isExpanded
    toggleLink.doClick()
    // compare data-test-state after click
    await condition(state != isExpanded, 15)
  }

  def ensureCollapsed(): Unit = {
    await condition {
      if (isExpanded) toggle()
      stateOf(toggleCssQuery) == "collapsed"
    }
  }

  def ensureExpanded(): Unit = {
    await condition {
      if (!isExpanded) toggle()
      stateOf(toggleCssQuery) == "expanded"
    }
  }

  def getInner: A = {
    ensureExpanded()
    awaitReady()
    inner
  }
}
