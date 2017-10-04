package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.{By, WebDriver}

case class Table(private val id: String)(implicit webDriver: WebDriver)
  extends Component(id) with Stateful {

  private val tableBody = findInner("table-body")

  private val filterField = findInner("filter-input")
  private val filterButton = findInner("filter-button")

  private def tab(name: String) = findInner(s"$name-tab")

  private val prevPageButton = Button(findInner("prev-page").queryString)
  private val nextPageButton = Button(findInner("next-page").queryString)
  private def pageButton(page: Int) = Button(findInner(s"page-$page").queryString)
  private val perPageSelector = findInner("per-page")

  override def awaitReady(): Unit = {
    awaitVisible()
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
    prevPageButton.doClick()
  }

  def goToNextPage(): Unit = {
    awaitReady()
    nextPageButton.doClick()
  }

  def goToPage(page: Int): Unit = {
    awaitReady()
    pageButton(page).doClick()
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
