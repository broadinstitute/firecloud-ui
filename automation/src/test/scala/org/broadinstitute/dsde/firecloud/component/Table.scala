package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.{By, WebDriver}

case class Table(private val id: String)(implicit webDriver: WebDriver)
  extends Component(id) with Stateful {

  private val tableBody = findInner("table-body")

  // TODO: figure out how to do sub-components properly

  private val filterField = findInner("filter-input")
  private val filterButton = findInner("filter-button")

  private def tab(name: String) = findInner(s"$name-tab")

  private val prevPageButton = findInner("prev-page")
  private val nextPageButton = findInner("next-page")
  private def pageButton(page: Int) = findInner(s"page-$page")
  private val perPageSelector = findInner("per-page")

  override def awaitReady(): Unit = {
    awaitVisible()
    await condition { getState == "ready" }
  }

  def filter(text: String): Unit = {
    searchField(filterField).value = text
    click on filterButton
    awaitReady()
  }

  def goToTab(tabName: String): Unit = {
    click on tab(tabName)
    awaitReady()
  }

  def readDisplayedTabCount(tabName: String): Int = {
    readText(tab(tabName)).replaceAll("\\D+","").toInt
  }

  def goToPreviousPage(): Unit = {
    click on prevPageButton
    awaitReady()
  }

  def goToNextPage(): Unit = {
    click on nextPageButton
    awaitReady()
  }

  def goToPage(page: Int): Unit = {
    click on pageButton(page)
    awaitReady()
  }

  def selectPerPage(perPage: Int): Unit = {
    singleSel(perPageSelector).value = perPage.toString
    awaitReady()
  }

  def getData: List[List[String]] = {
    import scala.collection.JavaConversions._

    awaitReady()
    val rows = tableBody.webElement.findElements(By.cssSelector("[data-test-class='table-row']")).toList
    rows.map(_.findElements(By.cssSelector("[data-test-class='table-cell']")).toList.map(_.getText))
  }
}
