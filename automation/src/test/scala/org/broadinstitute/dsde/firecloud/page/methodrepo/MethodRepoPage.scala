package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, OKCancelModal, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodRepoPage(implicit webDriver: WebDriver) extends BaseFireCloudPage with Page with PageUtil[MethodRepoPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#methods"

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

class CreateMethodModal(implicit webDriver: WebDriver) extends OKCancelModal {
  private val namespaceField = TextField("namespace-field")
  private val nameField = TextField("name-field")
  private val wdlField = WDLField("wdl-field")
  private val documentationField = MarkdownEditor("documentation-field")
  private val synopsisField = TextField("synopsis-field")

  def fillOut(attributes: Map[String, String]): Unit = {
    namespaceField.setText(attributes("namespace"))
    nameField.setText(attributes("name"))
    wdlField.fillWDL(attributes("payload"))
    documentationField.setText(attributes("documentation"))
    synopsisField.setText(attributes("synopsis"))
  }
}
