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

  private val tags = Tags("tags")
  private val consentCodes = Tags("consent-codes")

  override def awaitReady(): Unit = {
    libraryTable.awaitReady()
  }

  def validateLocation(): Unit = {
    libraryTable.awaitReady()
  }

  def hasDataset(name: String): Boolean = {
    doSearch(name)
    datasetLink(name).isVisible
  }

  def waitForDataset(name: String): Option[DataLibraryPage] = {
    retry[DataLibraryPage](10.seconds, 5.minutes)({
      val libraryPage = open
      if (libraryPage.hasDataset(name))
        Some(libraryPage)
      else None
    })
  }

  def openDataset(name: String): Unit = {
    Link(s"dataset-$name").doClick()
  }

  def doSearch(searchParameter: String): Unit = {
    searchField.setText(searchParameter)
    pressKeys("\n")
    libraryTable.awaitReady()
  }

  def openResearchPurposeModal(): ResearchPurposeModal = {
    rpModalLink.doClick()
    await ready ResearchPurposeModal()
  }

  def showsResearchPurposeCode(code: String): Boolean = {
    Label(s"$code-tag").isVisible
  }

  def getConsentCodes: Seq[String] = {
    consentCodes.awaitVisible()
    consentCodes.getTags
  }

  def getTags: Seq[String] = {
    tags.awaitVisible()
    tags.getTags
  }

  case class RequestAccessModal(implicit webDriver: WebDriver) extends OKCancelModal("") {
    val tcgaAccessText = "For access to TCGA controlled data please apply for access via dbGaP"
    val nonTcgaAccessText ="Please contact dbGAP and request access for workspace"
    def validateLocation: Boolean = {
      testId("message-modal-content").element != null
    }
    def readMessageModalText: String = {
      readText(testId("message-modal-content"))
    }
  }

  def enterRPOntologySearchText(text: String): Unit = {
    SearchField("ontology-autosuggest").setText(text)
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


case class ResearchPurposeModal(implicit webDriver: WebDriver) extends OKCancelModal("research-purpose-modal") {
  private def checkboxByCode(code: String) = Checkbox(s"$code-checkbox" inside this)

  def selectCheckbox(code: String): Unit = {
    checkboxByCode(code).ensureChecked()
  }
}

