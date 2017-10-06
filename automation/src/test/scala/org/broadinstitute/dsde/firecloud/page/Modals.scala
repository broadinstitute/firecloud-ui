package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Button
import org.openqa.selenium.WebDriver

abstract class OKCancelModal(implicit webDriver: WebDriver) extends FireCloudView {
  protected val okButton = Button("ok-button")
  protected val cancelButton = Button("cancel-button")
  protected val xButton = Button("x-button")

  def clickOk(): Unit = {
    okButton.doClick()
  }

  def clickCancel(): Unit = {
    cancelButton.doClick()
  }

  def clickXButton(): Unit = {
    xButton.doClick()
  }

  override def awaitReady(): Unit = {
    okButton.awaitVisible()
  }

  def awaitDismissed(): Unit = {
    okButton.awaitNotVisible()
  }

  def submit(): Unit = {
    clickOk()
    awaitDismissed()
  }

  def cancel(): Unit = {
    clickCancel()
    awaitDismissed()
  }

  def xOut(): Unit = {
    clickXButton()
    awaitDismissed()
  }
}

case class ErrorModal(implicit webDriver: WebDriver) extends OKCancelModal {
  def validateLocation(implicit webDriver: WebDriver): Boolean = {
    testId("error-modal").element != null
  }

  def getErrorText: String = {
    readText(testId("message-modal-content"))
  }

  override def awaitReady(): Unit = cancelButton.awaitVisible()

}

case class MessageModal(implicit webDriver: WebDriver) extends OKCancelModal {
  def validateLocation: Boolean = {
    testId("message-modal").element != null
  }
}

