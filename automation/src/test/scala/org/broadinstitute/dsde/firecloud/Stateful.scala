package org.broadinstitute.dsde.firecloud

import org.openqa.selenium.WebDriver

trait Stateful { this: FireCloudView =>
  val query: Query

  def getState(implicit webDriver: WebDriver): String = {
    stateOf(query)
  }

  def stateOf(query: Query)(implicit webDriver: WebDriver): String = {
    query.element.attribute("data-test-state").get
  }

  def awaitState(state: String)(implicit webDriver: WebDriver): Unit = {
    await condition { getState == state }
  }
}
