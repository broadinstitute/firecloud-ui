package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.{AuthenticatedPage, OKCancelModal, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodRepoPage(implicit webDriver: WebDriver) extends AuthenticatedPage with Page with PageUtil[MethodRepoPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#methods"

  override def awaitReady(): Unit = {
    MethodRepoTable.awaitReady()
  }

  private val newMethodButton = Button("create-method-button")

  def createNewMethod(attributes: Map[String, String]): Unit = {
    newMethodButton.doClick()
    val createMethodModal = await ready new CreateMethodModal()
    createMethodModal.fillOut(attributes)
    createMethodModal.submit()
  }

  object MethodRepoTable extends Table("methods-table") {
    private def methodLink(namespace: String, name: String) = Link(s"method-link-$namespace-$name")

    def hasMethod(namespace: String, name: String): Boolean = {
      val link = methodLink(namespace, name)
      link.isVisible
      methodLink(namespace, name).isVisible
    }

    def enterMethod(namespace: String, name: String): MethodDetailPage = {
      methodLink(namespace, name).doClick()
      await ready new MethodDetailPage(namespace, name)
    }
  }
}

class CreateMethodModal(implicit webDriver: WebDriver) extends OKCancelModal {
  private val namespaceField = TextField("namespace-field")
  private val nameField = TextField("name-field")
  private val synopsisField = TextField("synopsis-field")
  private val documentationField = TextArea("documentation-field")
  private val wdlField = WDLField("wdl-field")

  def fillOut(attributes: Map[String, String]): Unit = {
    namespaceField.setText(attributes("namespace"))
    nameField.setText(attributes("name"))
    synopsisField.setText(attributes("synopsis"))
    documentationField.setText(attributes("documentation"))
    wdlField.fillWDL(attributes("payload"))
  }
}
