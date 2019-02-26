package org.broadinstitute.dsde.firecloud.page.duos

import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver

/**
  * The DuosHomePage class should be the page where I have different methods to verify the homepage
  * after logging in.
  * @param webDriver
  */
class DuosHomePage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with PageUtil[DuosHomePage] {

  lazy override val url = "https://duos.dsde-dev.broadinstitute.org/#/login"

  override def awaitReady(): Unit = {
    super.awaitReady()
  }


  /**
    * Search for a dataset ID on the searchbar and advance to the next page.
    */
  def datasetSearch(): Unit = {
    val clicklogin = cssSelector("a[class='navbar-duos-button']")
    clicklogin.findElement.get.underlying.click()
    Thread.sleep(1000)
    goTo("https://duos.dsde-dev.broadinstitute.org/#/dataset_catalog")
    Thread.sleep(1000)
    val searchfor = "DUOS-000003"
    val searchBar = cssSelector("input[ng-model='searchDataset']")
    searchBar.findElement.get.underlying.click()
    Thread.sleep(1000)
    searchBar.findElement.get.asInstanceOf[ValueElement].value_=(searchfor)
    Thread.sleep( 1000)
    val check = cssSelector("label[class='regular-checkbox rp-choice-questions']")
    check.findElement.get.underlying.click()
    Thread.sleep(1000)
    val applyforaccess = cssSelector("button[ng-show='isResearcher']")
    Thread.sleep(1000)
    applyforaccess.findElement.get.underlying.click()
    Thread.sleep(1000)
  }
}
