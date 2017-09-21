package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{AuthenticatedPage, FireCloudView, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodRepoPage(implicit webDriver: WebDriver) extends AuthenticatedPage with Page with PageUtil[MethodRepoPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#methods2"

  trait UI extends super.UI {
    private val newMethodButtonQuery = testId("create-method-button")

    def clickNewMethodButton(): Unit =
      click on newMethodButtonQuery
  }

  object ui extends UI
}

class CreateMethodModal(implicit webDriver: WebDriver) extends FireCloudView {

  def createMethod(attributes: Map[String, String]): Unit = {
    ui.fillNamespaceField(attributes("namespace"))
    ui.fillNameField(attributes("name"))
    ui.fillSynopsisField(attributes("synopsis"))
    ui.fillDocumentationField(attributes("documentation"))
    ui.fillWDLField(attributes("payload"))

    ui.clickUploadButton()
  }

  object ui {
    private val namespaceFieldQuery = testId("namespace-field")
    private val nameFieldQuery = testId("name-field")
    private val synopsisFieldQuery = testId("synopsis-field")
    private val documentationFieldQuery = testId("documentation-field")
    private val wdlFieldQuery = testId("wdl-field")
    private val uploadButtonQuery = testId("upload-button")

    def fillNamespaceField(namespace: String): Unit =
      textField(namespaceFieldQuery).value = namespace

    def fillNameField(name: String): Unit =
      textField(nameFieldQuery).value = name

    def fillSynopsisField(synopsis: String): Unit =
      textField(synopsisFieldQuery).value = synopsis

    def fillDocumentationField(documentation: String): Unit =
      textArea(documentationFieldQuery).value = documentation

    def fillWDLField(wdl: String): Unit =
      textArea(wdlFieldQuery).value = wdl

    def clickUploadButton(): Unit =
      click on uploadButtonQuery
  }
}
