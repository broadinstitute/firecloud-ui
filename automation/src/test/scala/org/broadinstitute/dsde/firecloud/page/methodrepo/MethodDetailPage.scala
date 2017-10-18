package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.component.Button
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{AuthenticatedPage, MessageModal, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodDetailPage(namespace: String, name: String)(implicit webDriver: WebDriver) extends AuthenticatedPage
  with Page with PageUtil[MethodDetailPage] {

  override val url = s"${Config.FireCloud.baseUrl}#methods/$namespace/$name"

  private val redactButton = Button("redact-button")

  override def awaitReady(): Unit = {
    redactButton.awaitVisible()
  }

  def redact(): Unit = {
    redactButton.doClick()
    MessageModal().clickOk()
    // redact takes us back to the table:
    redactButton.awaitNotVisible()
  }
}
