package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodDetailPage(namespace: String, name: String)(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[MethodDetailPage] {

  override val url = s"${Config.FireCloud.baseUrl}#methods/$namespace/$name"

  private val redactButton = Button("redact-button")

  override def awaitReady(): Unit = {
    redactButton.awaitVisible()
  }

  def redact(): Unit = {
    redactButton.doClick()
    new OKCancelModal("confirm-redaction-modal").clickOk()
    // redact takes us back to the table:
    redactButton.awaitNotVisible()
  }
}
