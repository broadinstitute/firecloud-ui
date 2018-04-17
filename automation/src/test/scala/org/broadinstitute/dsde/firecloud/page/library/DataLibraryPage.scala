package org.broadinstitute.dsde.firecloud.page.library

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page
import scala.concurrent.duration.DurationLong

/**
  * Page class for the Data Library page.
  */
class DataLibraryPage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[DataLibraryPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#library"

  private val libraryTable = Table("library-table")
  private val searchField = SearchField("library-search-input")
  private val rpModalLink = Link("show-research-purpose-modal")

  private def datasetLink(name: String) = Link(s"dataset-$name")
  private def researchPurposeCode(code: String) = Label(s"$code-tag")

  private val tags = TagSelect("tags")
  private val consentCodes = TagSelect("consent-codes")

  //Hard Coded titles for facet sections in our UI code
  val cohortPhenotypeIndicationSection = "Cohort Phenotype/Indication"
  val experimentalStrategySection = "Experimental Strategy"
  val projectNameSection = "Project Name"
  val primaryDiseaseSiteSection = "Primary Disease Site"
  val dataUseLimitationSection = "Data Use Limitation"

  override def awaitReady(): Unit = {
    libraryTable.awaitReady()
  }

  def validateLocation(): Unit = {
    libraryTable.awaitReady()
  }

  def hasDataset(name: String): Boolean = {
    Link(CSSQuery(libraryTable.query.queryString + " " + testId(s"dataset-$name").queryString)).isVisible
  }

  def waitForDataset(name: String): Option[DataLibraryPage] = {
    val libraryPage = open
    retry[DataLibraryPage](10.seconds, 5.minutes)({
      doSearch(name)
      if (hasDataset(name))
        Some(libraryPage)
      else None
    })
  }

  def openDataset(name: String): Unit = {
    datasetLink(name).doClick()
  }

  def doSearch(searchParameter: String): Unit = {
    searchField.setText(searchParameter)
    pressKeys("\n")
    libraryTable.awaitReady()
  }

  def doTagSearch(tagWords: Seq[String]): Unit = {
    tags.doSearch(tagWords: _*)
    awaitReady()
  }

  def openResearchPurposeModal(): ResearchPurposeModal = {
    rpModalLink.doClick()
    await ready new ResearchPurposeModal()
  }

  def showsResearchPurposeCode(code: String): Boolean = {
    researchPurposeCode(code).isVisible
  }

  def getConsentCodes: Seq[String] = {
    consentCodes.awaitVisible()
    consentCodes.getTags
  }

  def getTags: Seq[String] = {
    tags.awaitVisible()
    tags.getTags
  }

  def getRows: List[Map[String, String]] = {
    libraryTable.getRows
  }

  case class RequestAccessModal(override implicit val webDriver: WebDriver) extends MessageModal {
    val tcgaAccessText = "For access to TCGA controlled data please apply for access via dbGaP"
    val nonTcgaAccessText = "Please contact dbGAP and request access for workspace"
  }
}


class ResearchPurposeModal(implicit webDriver: WebDriver) extends OKCancelModal("research-purpose-modal") {
  private def checkboxByCode(code: String) = Checkbox(s"$code-checkbox" inside this)
  private val ontologySearch = SearchField("ontology-autosuggest" inside this)

  def selectCheckbox(code: String): Unit = {
    checkboxByCode(code).ensureChecked()
  }

  def enterOntologySearchText(text: String): Unit = {
    ontologySearch.setText(text)
  }

  def isSuggestionVisible(suggestionTestId: String): Boolean = {
    await enabled testId(suggestionTestId) // has a timeout so test will not hang if suggestion never shows
    val ffiSuggestion = Button(suggestionTestId)
    ffiSuggestion.isVisible
  }

  def selectSuggestion(suggestionTestId: String, tagTestId: String): Unit = {
    val ffiSuggestion = Button(suggestionTestId)
    ffiSuggestion.doClick()
  }

  def isTagSelected(tagTestId: String): Boolean = {
    await enabled testId(tagTestId)
    Label(tagTestId).isVisible
  }

}

