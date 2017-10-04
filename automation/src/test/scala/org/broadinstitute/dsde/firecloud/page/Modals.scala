package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.openqa.selenium.WebDriver

abstract class OKCancelModal(implicit webDriver: WebDriver) extends FireCloudView {
  private val okButton: Query = testId("ok-button")
  private val cancelButton: Query = testId("cancel-button")

  def clickOk(): Unit = {
    click on okButton
    awaitReady()
  }
  def clickCancel(): Unit = {
    click on cancelButton
  }

  override def awaitReady(): Unit = {
    await visible okButton
  }
}

case class ErrorModal(implicit webDriver: WebDriver) extends OKCancelModal {
  def validateLocation(implicit webDriver: WebDriver): Boolean = {
    testId("error-modal").element != null
  }

  def getErrorText: String = {
    readText(testId("message-modal-content"))
  }

}

case class MessageModal(implicit webDriver: WebDriver) extends OKCancelModal {
  def validateLocation: Boolean = {
    testId("message-modal").element != null
  }
}

