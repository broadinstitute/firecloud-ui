package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.{By, WebDriver}

case class Table(private val id: String)(implicit webDriver: WebDriver)
  extends Component(id) with Stateful {

  private val tableBody = findInner("table-body")

  private val filterField = findInner("filter-input")
  private val filterButton = findInner("filter-button")

  private def tab(name: String) = findInner(s"$name-tab")

  private val prevPageButton = Button("prev-page")
  private val nextPageButton = Button("next-page")
  private def pageButton(page: Int) = Button(s"page-$page")
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
    awaitReady()
    readText(tab(tabName)).replaceAll("\\D+","").toInt
  }

  def goToPreviousPage(): Unit = {
    prevPageButton.doClick()
    awaitReady()
  }

  def goToNextPage(): Unit = {
    nextPageButton.doClick()
    awaitReady()
  }

  def goToPage(page: Int): Unit = {
    pageButton(page).doClick()
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
