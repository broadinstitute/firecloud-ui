package org.broadinstitute.dsde.firecloud.page.duos

import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver

/**
  * The DuosHomePage class should be the page where I have different methods to verify the homepage
  * after logging in.
  *
  * @param webDriver
  */
class DuosHomePage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with PageUtil[DuosHomePage] {

//  lazy override val url = "https://duos.dsde-dev.broadinstitute.org/#/login"

  lazy override val url = "https://duos.dsde-dev.broadinstitute.org/#/admin_console"

  override def awaitReady(): Unit = {
    super.awaitReady()
//    await condition {
//      val column = find(cssSelector("table th:nth-child(2)"))
//      var f: Boolean = false
//      if (column.isDefined) {
//        val txt = column.get.underlying.getText
//        f = txt.equalsIgnoreCase("Dataset ID")
//      }
//      f
//    }
  }

  /**
    * Search for a dataset ID on the searchbar and advance to the next page.
    */
//  def datasetSearch(): Unit = {
//    goTo("https://duos.dsde-dev.broadinstitute.org/#/dataset_catalog")
//    val searchfor = "DUOS-000003"
//    val searchBar = cssSelector("input[ng-model='searchDataset']")
//    searchBar.findElement.get.underlying.click()
//    searchBar.findElement.get.asInstanceOf[ValueElement].value_=(searchfor)
//    val check = cssSelector("label[class='regular-checkbox rp-choice-questions']")
//    check.findElement.get.underlying.click()
//    val applyforaccess = cssSelector("button[ng-show='isResearcher']")
//    applyforaccess.findElement.get.underlying.click()
//    Thread.sleep(1000)
//  }

def datasetSearch(): Unit = {
  Thread.sleep(1000)
  val datasetcatalog = cssSelector("a[href='#/dataset_catalog']")
  datasetcatalog.findElement.get.underlying.click()
//  goTo("https://duos.dsde-dev.broadinstitute.org/#/dataset_catalog")
//  val searchfor = "DUOS-000003"
//  val searchBar = cssSelector("input[ng-model='searchDataset']")
//  searchBar.findElement.get.underlying.click()
//  searchBar.findElement.get.asInstanceOf[ValueElement].value_=(searchfor)
//  val check = cssSelector("label[class='regular-checkbox rp-choice-questions']")
//  check.findElement.get.underlying.click()
//  val applyforaccess = cssSelector("button[ng-show='isResearcher']")
//  applyforaccess.findElement.get.underlying.click()
  Thread.sleep(1000)
}
}
