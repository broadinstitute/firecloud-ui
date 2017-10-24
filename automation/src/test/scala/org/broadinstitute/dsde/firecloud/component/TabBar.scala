package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class TabBar(private val queryString: QueryString = TestId("tabs"))(implicit webDriver: WebDriver) extends Component(queryString) {
  private def tab(name: String) = findInner(s"$name-tab")

  def goToTab(name: String): Unit = {
    awaitReady()
    click on tab(name)
  }
}
