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

  lazy override val url = "https://duos.dsde-dev.broadinstitute.org/#/dataset_catalog"

  override def awaitReady(): Unit = {
    super.awaitReady()
  }


  /**
    * Search for a dataset ID on the searchbar and advance to the next page.
    */
  def datasetSearch(): String = {
    val searchfor = "DUOS-000003"
    val searchBar = cssSelector("input[ng-model='searchDataset']")
    find(searchBar).get.underlying.click()
    find(searchBar).get.asInstanceOf[ValueElement].value_=(searchfor)
    //find(searchBar).get.underlying.sendKeys("DUOS-000003")
    val downloadBtn = cssSelector("button[ng-click='DatasetCatalog.download(objectIdList)']")
    find(downloadBtn).get.underlying.click()
    searchfor
  }
}


// hey i'm testing things 
