package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class TabBar(private val id: String = "tabs")(implicit webDriver: WebDriver) extends Component(id) {
  private def tab(name: String) = findInner(s"$name-tab")

  def goToTab(name: String): Unit = {
    awaitReady()
    click on tab(name)
  }
}
