package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Button
import org.broadinstitute.dsde.firecloud.component.Component._
import org.openqa.selenium.WebDriver

abstract class Modal(implicit webDriver: WebDriver) extends FireCloudView {
  protected val xButton = Button("x-button")

  override def awaitReady(): Unit = {
    xButton.awaitVisible()
  }

  def awaitDismissed(): Unit = {
    xButton.awaitNotVisible()
  }

  def clickXButton(): Unit = {
    xButton.doClick()
  }

  def xOut(): Unit = {
    clickXButton()
    awaitDismissed()
  }
}

abstract class OKCancelModal(implicit webDriver: WebDriver) extends Modal {
  protected val okButton = Button("ok-button")
  protected val cancelButton = Button("cancel-button")

  def clickOk(): Unit = {
    okButton.doClick()
  }

  def clickCancel(): Unit = {
    cancelButton.doClick()
  }

  def submit(): Unit = {
    clickOk()
    awaitDismissed()
  }

  def cancel(): Unit = {
    clickCancel()
    awaitDismissed()
  }
}

case class MessageModal(implicit webDriver: WebDriver) extends OKCancelModal {
  def validateLocation: Boolean = {
    testId("message-modal-content").element != null
  }

  def readMessageModalText: String = {
    readText (testId ("message-modal-content"))
  }

  def getMessageText: String = {
    readText(testId("message-modal-content"))
  }

  override def awaitReady(): Unit = okButton.awaitVisible()

}

case class SynchronizeMethodAccessModal(implicit webDriver: WebDriver) extends OKCancelModal {
  protected val grantButtonId = "grant-read-permission-button"

  def validateLocation: Boolean = {
    awaitReady()
    testId("method-access-content").element != null
  }

  override def clickOk(): Unit = {
    Button(grantButtonId).doClick()
  }

  override def awaitReady(): Unit = await.visible(testId(grantButtonId), 1)

}



