package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.{FireCloudView, Stateful}
import org.openqa.selenium.WebDriver

case class Collapse[A <: FireCloudView](queryString: QueryString, private val inner: A)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful {

  private val toggleCssQuery: CssSelectorQuery = findInner("toggle")
  private val toggleLink = Link(CSSQuery(toggleCssQuery.queryString))

  override def awaitReady(): Unit = inner.awaitReady()

  def isExpanded: Boolean = {
    awaitVisible() // Check Collapse div visibility
    await visible toggleCssQuery // Check toggle link visibility
    stateOf(toggleCssQuery) == "expanded"
  }

  def toggle(): Unit = {
    await visible toggleCssQuery
    val origState = stateOf(toggleCssQuery)
    toggleLink.doClick()
    // data-test-state should change after click
    await condition(origState != stateOf(toggleCssQuery), 15)
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
    inner
  }
}
