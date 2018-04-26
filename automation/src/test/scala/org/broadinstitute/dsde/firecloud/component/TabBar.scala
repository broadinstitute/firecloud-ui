package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class TabBar(private val queryString: QueryString = TestId("tabs"))(implicit webDriver: WebDriver) extends Component(queryString) {

  def goToTab(name: String): Unit = {
    awaitReady()
    Link(s"$name-tab" inside this).doClick()
    await notVisible cssSelector("[data-test-id=spinner]")
  }

}