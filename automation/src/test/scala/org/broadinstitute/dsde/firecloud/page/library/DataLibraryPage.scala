package org.broadinstitute.dsde.firecloud.page.library

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

import scala.concurrent.duration.DurationLong

/**
  * Page class for the Data Library page.
  */
class DataLibraryPage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[DataLibraryPage] with LazyLogging {

  override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#library"

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

  /**
    *
    * @param text
    * @return Search textfield's dropdown element id
    */
  def enterOntologySearchText(text: String): Seq[String] = {
    ontologySearch.setText(s"$text ") // appends a whitespace

    val uel = ontologySearch.query.element.underlying

    logger.warn(s"==========>>>>>>>>>> enterOntologySearchText uel is ${uel.getTagName} : ${uel.toString}")

    // Thread sleep 500 // micro sleep before checking for visibility

    // wait for the dropdown to be displayed
    await condition uel.isDisplayed

    // and wait for it to be expanded
    // await condition uel.getAttribute("aria-expanded") == "true"

    // look for the "aria-owns" or "aria-controls" attributes. Either could be populated.
    val ownedId = Option(uel.getAttribute("aria-owns"))
    val controlledId = Option(uel.getAttribute("aria-controls"))

    val dropdownIdOption = (ownedId ++ controlledId).headOption

    logger.warn(s"==========>>>>>>>>>> enterOntologySearchText ownedId is $ownedId : controlledId is $controlledId")

    assert(dropdownIdOption.nonEmpty, s"Could not determine dropdownId from aria-owns [$ownedId] : aria-controls [$controlledId]")

    val dropdownId = dropdownIdOption.get // safe given the previous assert

    import org.openqa.selenium.JavascriptExecutor
    val executor = webDriver.asInstanceOf[JavascriptExecutor]


    val uelAttrs = executor.executeScript("var items = {}; for (index = 0; index < arguments[0].attributes.length; ++index) { items[arguments[0].attributes[index].name] = arguments[0].attributes[index].value }; return items;", uel)

    logger.warn(s"==========>>>>>>>>>> enterOntologySearchText uelAttrs is $uelAttrs")

    logger.warn(s"==========>>>>>>>>>> enterOntologySearchText dropdownId is [$dropdownId]")


    val listOptionXpath = s"//div[@id='$dropdownId']/ul[@role='listbox']/li[@role='option']"
    // wait for dropdown to contain at least one option
    await condition {
      find(xpath(s"//div[@id='$dropdownId']")).exists(_.isDisplayed)
      val firstFoundValues = findAll(xpath(listOptionXpath)).map(_.text).toSeq
      logger.warn(s"==========>>>>>>>>>> enterOntologySearchText firstFoundValues is [${firstFoundValues.toList}]")
      firstFoundValues.nonEmpty // getting Element's text force screen scroll if item is outside of viewport
    }

    val secondFoundValues = findAll(xpath(listOptionXpath)).map(_.text).toSeq
    logger.warn(s"==========>>>>>>>>>> enterOntologySearchText secondFoundValues is [${secondFoundValues.toList}]")
    secondFoundValues
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

