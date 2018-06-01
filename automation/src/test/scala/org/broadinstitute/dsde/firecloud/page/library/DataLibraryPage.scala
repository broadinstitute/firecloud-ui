package org.broadinstitute.dsde.firecloud.page.library

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

import scala.concurrent.duration.DurationLong
import scala.util.{Failure, Success, Try}

/**
  * Page class for the Data Library page.
  */
class DataLibraryPage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[DataLibraryPage] with LazyLogging {

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
    logger.info(s"Waiting for UI to show dataset: $name")
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

  case class RequestAccessModal()(override implicit val webDriver: WebDriver) extends MessageModal {
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
    ontologySearch.setText(s"$text ") // appends a whitespace
    Thread sleep 100 // micro sleep before look for spinner
    val dropdownId = ontologySearch.query.element.underlying.getAttribute("aria-owns")
    await visible id(dropdownId)
    Try{
      await condition invisibleSpinner
    } match {
      case Success(_) =>
      case Failure(_) =>
        logger.warn(s"Retrying enterOntologySearchText($text)")
        ontologySearch.query.element.underlying.clear()
        ontologySearch.setText(text) // try setText again
    }
  }

  def isSuggestionVisible(suggestionTestId: String): Boolean = {
    Try {
      Link(suggestionTestId inside this).awaitVisible()
    } match {
      case Success(_) => Link(suggestionTestId inside this).isVisible
      case Failure(_) => false
    }
  }

  def selectSuggestion(suggestionTestId: String): Unit = {
    Link(suggestionTestId inside this).doClick()
    // selecting something should clear out the search field:
    await condition { ontologySearch.getText == "" }
  }

  def isTagSelected(tagTestId: String): Boolean = {
    await enabled testId(tagTestId)
    Label(tagTestId inside this).isVisible
  }

}

