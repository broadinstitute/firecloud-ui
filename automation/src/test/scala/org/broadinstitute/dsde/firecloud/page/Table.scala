package org.broadinstitute.dsde.firecloud.page

import org.openqa.selenium.WebDriver

class Table(rootId: String)(implicit webDriver: WebDriver) extends FireCloudView {

  private val tableElement = testId(rootId)

  def findInner(id: String): CssSelectorQuery = testId(id) inside tableElement

  private val filterField = findInner("filter-input")
  private val filterButton = findInner("filter-button")

  private def tab(name: String) = findInner(s"$name-tab")

  private val prevPageButton = findInner("prev-page")
  private val nextPageButton = findInner("next-page")
  private def pageButton(page: Int) = findInner(s"page-$page")
  private val perPageSelector = findInner("per-page")

  def filter(text: String): Unit = {
    searchField(filterField).value = text
    click on filterButton
  }

  def goToTab(tabName: String): Unit = {
    click on tab(tabName)
  }

  def goToPreviousPage(): Unit = click on prevPageButton

  def goToNextPage(): Unit = click on nextPageButton

  def goToPage(page: Int): Unit = click on pageButton(page)

  def selectPerPage(perPage: Int): Unit = singleSel(perPageSelector).value = perPage.toString
}
