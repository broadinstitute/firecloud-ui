package org.broadinstitute.dsde.firecloud.page.components

import org.openqa.selenium.WebDriver

case class TabBar(private val id: String = "tabs")(implicit webDriver: WebDriver) extends Component(id) {
  private def tab(name: String) = findInner(s"$name-tab")

  def goToTab(name: String): Unit = {
    awaitEnabled()
    click on tab(name)
  }
}
