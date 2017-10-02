package org.broadinstitute.dsde.firecloud.page.components

import org.broadinstitute.dsde.firecloud.page.Stateful
import org.openqa.selenium.{By, WebDriver}

case class Table(private val id: String)(implicit webDriver: WebDriver)
  extends Component(id) with Stateful {

  private val tableBody = findInner("table-body")

  private val filterField = findInner("filter-input")
  private val filterButton = findInner("filter-button")

  private def tab(name: String) = findInner(s"$name-tab")

  private val prevPageButton = findInner("prev-page")
  private val nextPageButton = findInner("next-page")
  private def pageButton(page: Int) = findInner(s"page-$page")
  private val perPageSelector = findInner("per-page")

  def awaitReady(): Unit = {
    awaitEnabled()
    await condition { getState == "ready" }
  }

  def filter(text: String): Unit = {
    awaitReady()
    searchField(filterField).value = text
    click on filterButton
  }

  def goToTab(tabName: String): Unit = {
    awaitReady()
    click on tab(tabName)
  }

  def readDisplayedTabCount(tabName: String): Int = {
    awaitReady()
    readText(tab(tabName)).replaceAll("\\D+","").toInt
  }

  def goToPreviousPage(): Unit = {
    awaitReady()
    click on prevPageButton
  }

  def goToNextPage(): Unit = {
    awaitReady()
    click on nextPageButton
  }

  def goToPage(page: Int): Unit = {
    awaitReady()
    click on pageButton(page)
  }

  def selectPerPage(perPage: Int): Unit = {
    awaitReady()
    singleSel(perPageSelector).value = perPage.toString
  }

  def getData: List[List[String]] = {
    import scala.collection.JavaConversions._

    awaitReady()
    val rows = tableBody.webElement.findElements(By.cssSelector("[data-test-class='table-row']")).toList
    rows.map(_.findElements(By.cssSelector("[data-test-class='table-cell']")).toList.map(_.getText))
  }
}
