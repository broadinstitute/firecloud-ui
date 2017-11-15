package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.component.Component._
import org.openqa.selenium.WebDriver


case class TabBar(private val queryString: QueryString = TestId("tabs"))(implicit webDriver: WebDriver) extends Component(queryString) {
  private def tabtestId(name: String): String = s"$name-tab"
  private def tab(name: String) = findInner(tabtestId(name))

  def goToTab(name: String): Unit = {
    awaitReady()
    //the whitelisted Notebooks tab loads after the rest of the tabs
    if (name == "Notebooks") (Label(tabtestId(name)).awaitVisible())
    click on tab(name)
  }

}