package org.broadinstitute.dsde.firecloud.page.user

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component.Button
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.openqa.selenium.WebDriver

class TermsOfServicePage(implicit webDriver: WebDriver) extends AuthenticatedPage {

  lazy override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}"

  private val acceptButton = Button("accept-button")

  override def awaitReady(): Unit = acceptButton.awaitVisible()

  def accept(): Unit = {
    acceptButton.doClick()
  }
}
