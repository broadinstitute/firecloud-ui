package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{AuthenticatedPage, MessageModal, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodDetailPage(namespace: String, name: String)(implicit webDriver: WebDriver) extends AuthenticatedPage
  with Page with PageUtil[MethodDetailPage] {

  override val url = s"${Config.FireCloud.baseUrl}#methods2/$namespace/$name"

  override def awaitLoaded(): MethodDetailPage = {
    await enabled ui.redactButtonQuery
    this
  }

  trait UI extends super.UI {
    private[MethodDetailPage] val redactButtonQuery = testId("redact-button")

    def redact(): Unit = {
      click on redactButtonQuery
      MessageModal().clickOk()
    }
  }

  object ui extends UI
}
