package org.broadinstitute.dsde.firecloud.page

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Button
import org.broadinstitute.dsde.firecloud.component.Component._
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
    testId("message-modal-content").element != null
  }

  def getMessageText: String = {
    readText(testId("message-modal-content"))
  }

  override def awaitReady(): Unit = cancelButton.awaitVisible()

}

case class SynchronizeMethodAccessModal(implicit webDriver: WebDriver) extends OKCancelModal {
  protected val grantButtonId = "grant-read-permission-button"

  def validateLocation: Boolean = {
//    awaitReady()
    testId("method-access-content").findElement != None
  }

  override def clickOk(): Unit = {
    Button(grantButtonId).doClick()
  }

  override def awaitReady(): Unit = await.visible(testId(grantButtonId), 1)

}

case class SynchronizeMethodAccessUnableModal(implicit webDriver: WebDriver) extends OKCancelModal {
  def validateLocation: Boolean = {
//    awaitReady()
    testId("method-access-unable").findElement != None
  }

  override def awaitReady(): Unit = await.visible(testId("ok-button"), 1)

}



