package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.openqa.selenium.WebDriver

abstract class Component(id: String)(implicit webDriver: WebDriver) extends FireCloudView {
  val element: CssSelectorQuery = testId(id)

  def findInner(id: String): CssSelectorQuery = testId(id) inside element

  def awaitVisible(): Unit = await visible element

  def isVisible: Boolean = find(element).isDefined

  override def awaitReady(): Unit = awaitVisible()
}
