package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.{Persists, Stateful}
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.{By, Keys, WebDriver}

import scala.util.Try

case class Table(queryString: QueryString)(implicit webDriver: WebDriver)
  extends Component(queryString) with Stateful with Persists {

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
  private val tableMessage = Label("message-well" inside this)

  override def awaitReady(): Unit = {
    awaitVisible()
    await condition { getState == "ready" }
  }

  /**
    * Determine whether this table is empty, without any table row.
    * If any exception is thrown, this method returns false.
    * @return True if table is empty (no rows)
    */
  def isEmpty(): Boolean = {
    try {
      tableMessage.isVisible || tableBody.webElement.findElements(By.cssSelector("[data-test-class='table-row']")).isEmpty
    } catch {
      case e: Exception => false // not able to determine table is empty
    }
  }

  def filter(text: String): Unit = {
    // filtering on a empty table doesn't make sense, unless text string is empty.
    // if text is empty, user is using this method to clear filtered results.
    if (text.isEmpty || !isEmpty()) {
      filterField.setText(text)
      clickFilterButton()
      try {
        await condition (filterField.query.element.underlying.getAttribute("value") == text, 2)
      } catch {
        case _: Exception => logger.warn(s"timed out (2 seconds) waiting for filter textfield's attribute == ${text}")
      }
    }
  }

  def clickFilterButton(): Unit = {
    filterButton.doClick()
    // waiting up to 1 second for data-test-state change to "Loading". some tables don't change data-test-state at all
    // to avoid race condition with table.awaitReady()
    try {
      await condition(getState == "loading", 1)
    } catch {
      case _: Exception => // ignore timeout exception
    }
    awaitReady()
  }

  def goToTab(tabName: String): Unit = {
    Link(s"$tabName-tab" inside this).doClick()
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
    import scala.collection.JavaConverters._
    val rows = tableBody.webElement.findElements(By.cssSelector("[data-test-class='table-row']")).asScala.toList
    rows.map(_.findElements(By.cssSelector("[data-test-class='table-cell']")).asScala.toList.map(_.getText))
  }

  def getHref: List[String] = {
    import scala.collection.JavaConverters._
    tableBody.webElement.findElements(By.cssSelector("[data-test-class='table-cell'] a")).asScala.toList.map(_.getAttribute("href"))
  }

  def readColumnHeaders: List[String] = {
    readAllText(columnHeaders)
  }

  def clearFilter(): Unit = {
    filter("")
  }

  def hideColumn(header: String): Unit = {
    if (readAllText(columnHeaders).contains(header)) {
      val dropdownCssQuery = clickColumnEditorButton()
      val colToBeHidden = Checkbox(CSSQuery(s"${dropdownCssQuery.queryString} [data-test-id=$header-column-toggle]"))
      colToBeHidden.ensureUnchecked()
      val action = new Actions(webDriver)
      action.sendKeys(Keys.ESCAPE).perform()
    }
  }

  def moveColumn(header: String, otherHeader: String): Unit = {
    val allHeaders = readAllText(columnHeaders)
    if (allHeaders.contains(header) && allHeaders.contains(otherHeader)) {
      val dropdownCssQuery = clickColumnEditorButton()
      val colToBeMoved = CssSelectorQuery(s"${dropdownCssQuery.queryString} [data-test-id=$header-grab-icon]").element.underlying
      val placeToMoveCol = CssSelectorQuery(s"${dropdownCssQuery.queryString} [data-test-id=$otherHeader-grab-icon]").element.underlying
      val action = new Actions(webDriver)
      action.clickAndHold(colToBeMoved).moveToElement(placeToMoveCol).release().build().perform()
    }
  }

  def getRows: List[Map[String,String]] = {
    val rows: List[List[String]] = getData
    val cols = readColumnHeaders

    val map = for (row <- rows) yield {
      (cols zip(row)).toMap
    }
    map
  }

  /**
    * click button "Column Editor" invokes dropdown
    *
    * @return Css selector string that finds dropdown WebElement
    */
  private def clickColumnEditorButton(): CssSelectorQuery = {
    // find dynamic dropdown id
    val dropdownId = columnEditorButton.query.element.underlying.findElement(By.xpath("./..")).getAttribute("data-toggle")
    val css = CssSelectorQuery(s"#${dropdownId}")
    columnEditorButton.doClick()
    Try(
      await condition {
        val dropdown: Option[Element] = find(css)
        dropdown.get.underlying.getAttribute("class").contains("is-open")
      }
    ).recover {
      case _ => columnEditorButton.doClick() // retry click when dropdown is not found or open
    }
    css
  }

}
