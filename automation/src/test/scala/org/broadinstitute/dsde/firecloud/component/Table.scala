package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{By, Keys, WebDriver}

case class Table(queryString: QueryString)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful {

  private val tableBody = findInner("table-body")
  private val columnHeaders = findInner("column-header")

  private val filterField = SearchField("filter-input" inside this)
  private val filterButton = Button("filter-button" inside this)

  private def tab(name: String) = findInner(s"$name-tab")

  private val prevPageButton = Button("prev-page" inside this)
  private val nextPageButton = Button("next-page" inside this)
  private def pageButton(page: Int) = Button(s"page-$page" inside this)
  private val perPageSelector = Select("per-page" inside this)
  private val columnEditorButton = Button("columns-button" inside this)

  override def awaitReady(): Unit = {
    awaitVisible()
    await condition { getState == "ready" }
  }

  def filter(text: String): Unit = {
    filterField.setText(text)
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
    perPageSelector.select(perPage.toString)
    awaitReady()
  }

  def getData: List[List[String]] = {
    import scala.collection.JavaConversions._
    val rows = tableBody.webElement.findElements(By.cssSelector("[data-test-class='table-row']")).toList
    rows.map(_.findElements(By.cssSelector("[data-test-class='table-cell']")).toList.map(_.getText))
  }

  def readColumnHeaders: List[String] = {
    readAllText(columnHeaders)
  }

  def clearFilter(): Unit = {
    filter("")
  }

  def hideColumn(header: String): Unit = {
    if (readAllText(columnHeaders).contains(header)) {
      columnEditorButton.doClick
      val colToBeHidden = Checkbox(s"$header-column-toggle" inside this)
      colToBeHidden.ensureUnchecked()
      val action = new Actions(webDriver)
      action.sendKeys(Keys.ESCAPE).perform()
    }
  }
}
