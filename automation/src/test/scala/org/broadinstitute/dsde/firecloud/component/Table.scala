package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{By, Keys, WebDriver}

case class Table(private val id: String)(implicit webDriver: WebDriver)
  extends Component(id) with Stateful {

  private val tableBody = findInner("table-body")

  // TODO: figure out how to do sub-components properly

  private val filterField = findInner("filter-input")
  private val filterButton = Button("filter-button" inside this)

  private def tab(name: String) = findInner(s"$name-tab")

  private val prevPageButton = findInner("prev-page")
  private val nextPageButton = findInner("next-page")
  private def pageButton(page: Int) = findInner(s"page-$page")
  private val perPageSelector = findInner("per-page")
  private val columnHeaders = testId("column-header")
  private val columnEditorButton = Button("columns-button")


  override def awaitReady(): Unit = {
    awaitVisible()
    await condition { getState == "ready" }
  }

  def filter(text: String): Unit = {
    searchField(filterField).value = text
    filterButton.doClick()
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

  def readColumnHeaders: List[String] = {
    awaitReady()
    readAllText(columnHeaders)
  }

  def clearFilter: Unit = {
    searchField(filterField).value = ""
    click on filterButton
    awaitReady()
  }

  def hideColumn(header: String) = {
    if (readAllText(columnHeaders).contains(header)) {
      columnEditorButton.doClick
      val colToBeHidden = Checkbox(s"$header-column-toggle")
      colToBeHidden.ensureUnchecked()
      val action = new Actions(webDriver)
      action.sendKeys(Keys.ESCAPE).perform()
    }
  }
}
