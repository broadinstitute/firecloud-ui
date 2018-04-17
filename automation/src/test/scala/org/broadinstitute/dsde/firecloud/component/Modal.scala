package org.broadinstitute.dsde.firecloud.component

import org.broadinstitute.dsde.firecloud.{ReadyComponent, SignalsReadiness, Stateful}
import org.broadinstitute.dsde.workbench.service.test.Awaiter
import org.openqa.selenium.WebDriver

abstract class Modal(id: String)(implicit val webDriver: WebDriver) extends Component(TestId(id)) with ReadyComponent {
  protected val xButton = Button("x-button" inside this)

  override val readyComponent: Awaiter = xButton

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

class OKCancelModal(id: String)(override implicit val webDriver: WebDriver) extends Modal(id) {
  protected def innerElement(innerId: String): QueryString = s"$id-$innerId" inside this

  protected val header = Label(innerElement("header"))
  protected val content = Label(innerElement("content"))

  protected val okButton = Button(innerElement("submit-button"))
  protected val cancelButton = Button(innerElement("cancel-button"))

  override val readyComponent: Awaiter = okButton

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

class ConfirmModal(implicit webDriver: WebDriver) extends OKCancelModal("confirmation-modal")

class GCSFilePreviewModal(implicit webDriver: WebDriver) extends OKCancelModal("preview-modal") with SignalsReadiness {
  private val googleBucket = Label("google-bucket-content" inside this)
  private val googleObject = Label("object-content" inside this)

  private val previewMessage = Label("preview-message" inside this)
  private val errorMessage = Label("error-message" inside this)

  private val previewPane = Label("preview-pane" inside this)

  def getBucket: String = googleBucket.getText
  def getObject: String = googleObject.getText
  def getPreviewMessage: String = previewMessage.getText
  def getErrorMessage: String = errorMessage.getText

  def awaitPreviewVisible(): Unit = previewPane.awaitVisible()
  def getFilePreview: String = {
    awaitPreviewVisible()
    previewPane.getText
  }
}
