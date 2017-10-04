package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.openqa.selenium.WebDriver

abstract class Component(id: String, raw: Boolean = false)(implicit webDriver: WebDriver) extends FireCloudView {
  val element: CssSelectorQuery = if (raw) CssSelectorQuery(id) else testId(id)

  def findInner(id: String): CssSelectorQuery = testId(id) inside element

  def awaitVisible(): Unit = await visible element
  def awaitNotVisible(): Unit = await notVisible element

  def isVisible: Boolean = find(element).isDefined
  def isEnabled: Boolean = enabled(element)

  override def awaitReady(): Unit = awaitVisible()
}
