package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class TabBar(private val queryString: QueryString = TestId("tabs"))(implicit webDriver: WebDriver) extends Component(queryString) {

  def goToTab(name: String): Unit = {
    awaitReady()
    Link(s"$name-tab" inside this).doClick()
    await condition (invisibleSpinner)
  }

  // Making a new variable called anaquery (analysis tab)
//  val anaquery = CssSelectorQuery("div[data-test-id='Analysis-tab'])")
//  def mytest(): Unit ={
//    //anaquery.findElement.get.underlying.getText()
//    anaquery.findElement.get.text()
//    System.out.print("this is my output" + anaquery)
//  }

}