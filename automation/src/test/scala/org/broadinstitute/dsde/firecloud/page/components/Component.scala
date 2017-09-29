package org.broadinstitute.dsde.firecloud.page.components

import org.broadinstitute.dsde.firecloud.page.FireCloudView
import org.openqa.selenium.WebDriver

abstract class Component(id: String)(implicit webDriver: WebDriver) extends FireCloudView {
  private val element: CssSelectorQuery = testId(id)

  def findInner(id: String): CssSelectorQuery = testId(id) inside element

  def awaitEnabled(): Unit = {
    await enabled element
  }

  def getState: String = {
    stateOf(element)
  }

  def stateOf(query: Query): String = {
    query.element.attribute("data-test-state").get
  }
}
