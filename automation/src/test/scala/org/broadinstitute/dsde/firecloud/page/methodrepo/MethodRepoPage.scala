package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodRepoPage(implicit webDriver: WebDriver) extends BaseFireCloudPage with Page with PageUtil[MethodRepoPage] {

  override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#methods"

  override def awaitReady(): Unit = methodRepoTable.awaitReady()

  val methodRepoTable = new MethodRepoTable()
  private val newMethodButton = Button("create-method-button")

  def createNewMethod(attributes: Map[String, String]): Unit = {
    newMethodButton.doClick()
    val createMethodModal = await ready new CreateMethodModal()
    createMethodModal.fillOut(attributes)
    createMethodModal.submit()
  }
}

class CreateMethodModal(implicit webDriver: WebDriver) extends OKCancelModal("create-new-method-modal") {
  private val namespaceField = TextField("namespace-field" inside this)
  private val nameField = TextField("name-field" inside this)
  private val wdlField = WDLField("wdl-field") // Unable to "inside" this one, but unlikely to collide with anything
  private val documentationField = MarkdownEditor("documentation-field" inside this)
  private val synopsisField = TextField("synopsis-field" inside this)
  private val uploadButton = Button("create-new-method-modal-submit-button" inside this)

  override def awaitReady(): Unit = {
    super.awaitReady()
    uploadButton.awaitEnabledState()
  }

  def fillOut(attributes: Map[String, String]): Unit = {
    namespaceField.setText(attributes("namespace"))
    nameField.setText(attributes("name"))
    wdlField.fillWDL(attributes("payload"))
    documentationField.setText(attributes("documentation"))
    synopsisField.setText(attributes("synopsis"))
  }
}
