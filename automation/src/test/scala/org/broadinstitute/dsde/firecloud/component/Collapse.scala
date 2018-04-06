package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.{FireCloudView, Stateful}
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{WebDriver, WebDriverException}

case class Collapse[A <: FireCloudView](queryString: QueryString, private val inner: A)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful {
  private val toggleComponent: CssSelectorQuery = findInner("toggle")

  override def awaitReady(): Unit = inner.awaitReady()

  def isExpanded: Boolean = {
    awaitVisible()
    stateOf(toggleComponent) == "expanded"
  }

  def toggle(): Unit = {
    await visible toggleComponent
    val origState = stateOf(toggleComponent)
    try {
      click on toggleComponent
    } catch {
      // retry click alternative. probably should be used in doClick
      // https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/2766
      case e: WebDriverException => new Actions(webDriver).moveToElement(toggleComponent.element.underlying).click().build.perform()
    }
    // data-test-state should change after click
    await condition(origState != stateOf(toggleComponent), 15)
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
