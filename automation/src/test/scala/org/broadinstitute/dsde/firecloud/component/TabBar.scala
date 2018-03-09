package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.component.Component._
import org.openqa.selenium.WebDriver


case class TabBar(private val queryString: QueryString = TestId("tabs"))(implicit webDriver: WebDriver) extends Component(queryString) {

  private def tab(name: String) = findInner(s"$name-tab")

  def goToTab(name: String): Unit = {
    awaitReady()
    click on tab(name)
  }

  /**
    * If WebElement `tab` has some children div, then it's open.
    * @param name
    */
  def isTabOpen(name: String): Unit = {
    !findAll(s"${tab(name).queryString} > div").isEmpty
  }

}