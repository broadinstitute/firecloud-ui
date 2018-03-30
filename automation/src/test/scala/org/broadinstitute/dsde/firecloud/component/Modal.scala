package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.Stateful
import org.openqa.selenium.{By, WebDriver}

abstract class Modal(id: String)(implicit webDriver: WebDriver) extends Component(TestId(id)) {
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

class OKCancelModal(id: String)(implicit webDriver: WebDriver) extends Modal(id) {
  protected def innerElement(innerId: String): QueryString = s"$id-$innerId" inside this

  protected val header = Label(innerElement("header"))
  protected val content = Label(innerElement("content"))

  protected val okButton = Button(innerElement("submit-button"))
  protected val cancelButton = Button(innerElement("cancel-button"))

  override def awaitReady(): Unit = {
    okButton.awaitVisible()
  }

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

class MessageModal(implicit webDriver: WebDriver) extends OKCancelModal("message-modal") {
  def getMessageText: String = content.getText
}

class ErrorModal(implicit webDriver: WebDriver) extends OKCancelModal("error-modal")

class SynchronizeMethodAccessModal(id: String)(implicit webDriver: WebDriver) extends OKCancelModal(id) {
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

class PreviewModal(id: String)(implicit webDriver: WebDriver) extends OKCancelModal(id) with Stateful {
  private val loadingState = "loading"
  private val doneLoading = "done"

  def getBucket = testId("Google Bucket").webElement.findElement(By.xpath("..")).getText()
  def getObject = testId("Object").webElement.findElement(By.xpath("..")).getText()
  def getPreviewMessage = testId("preview-message").webElement.getText()
  def getErrorMessage = testId("error-message").webElement.getText
  def awaitDoneState(): Unit = awaitState(doneLoading)

  override def awaitReady(): Unit = okButton.awaitReady()

}



