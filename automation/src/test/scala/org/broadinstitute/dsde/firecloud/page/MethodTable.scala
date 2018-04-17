package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.openqa.selenium.WebDriver

abstract class MethodTable[T](implicit webDriver: WebDriver) extends Table("methods-table") {
  private def methodLink(namespace: String, name: String) = Link(s"method-link-$namespace-$name")

  def hasMethod(namespace: String, name: String): Boolean = {
    methodLink(namespace, name).isVisible
  }

  def enterMethod(namespace: String, name: String): T = {
    filter(name)
    methodLink(namespace, name).doClick()
    awaitInnerPage(namespace, name)
  }

  protected def awaitInnerPage(namespace: String, name: String): T
}
