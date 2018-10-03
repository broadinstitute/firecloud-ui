package org.broadinstitute.dsde.firecloud.page.user

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.AuthenticatedPage
import org.openqa.selenium.WebDriver

class TermsOfServicePage(implicit webDriver: WebDriver) extends AuthenticatedPage {
  override def awaitReady(): Unit = await visible (testId("accept-button"), 5)

  private val acceptButton = Button(TestId("accept-button"))

  def accept(): Unit = {
    acceptButton.doClick()
  }
}
