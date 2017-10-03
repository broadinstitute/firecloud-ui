package org.broadinstitute.dsde.firecloud.page

import org.openqa.selenium.WebDriver

trait Stateful extends FireCloudView {
  val element: Query

  def getState(implicit webDriver: WebDriver): String = {
    stateOf(element)
  }

  def stateOf(query: Query)(implicit webDriver: WebDriver): String = {
    query.element.attribute("data-test-state").get
  }
}
