package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

abstract class Modal(queryString: QueryString)(implicit webDriver: WebDriver) extends Component(queryString) {
  protected val xButton = Button("x-button" inside this)

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

abstract class OKCancelModal(queryString: QueryString)(implicit webDriver: WebDriver) extends Modal(queryString) {
  protected val okButton = Button("ok-button" inside this)
  protected val cancelButton = Button("cancel-button" inside this)

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

case class MessageModal(queryString: QueryString)(implicit webDriver: WebDriver) extends OKCancelModal(queryString) {
  def validateLocation: Boolean = {
    testId("message-modal-content").element != null
  }

  def getMessageText: String = {
    readText(testId("message-modal-content"))
  }

  override def awaitReady(): Unit = okButton.awaitVisible()

}

case class SynchronizeMethodAccessModal(queryString: QueryString)(implicit webDriver: WebDriver) extends OKCancelModal(queryString) {
  protected val grantButton = Button("grant-read-permission-button" inside this)

  def validateLocation: Boolean = {
    awaitReady()
    testId("method-access-content").element != null
  }

  override def clickOk(): Unit = {
    grantButton.doClick()
  }

  override def awaitReady(): Unit = grantButton.awaitReady()

}



