package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.SubmissionDetailsPage
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.openqa.selenium.WebDriver
import org.scalatest.concurrent.Eventually
import org.scalatest.selenium.Page
import org.scalatest.time.{Seconds, Span}

import scala.util.{Failure, Success, Try}

class WorkspaceMethodConfigDetailsPage(namespace: String, name: String, methodConfigNamespace: String, val methodConfigName: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceMethodConfigDetailsPage] with LazyLogging with Eventually {

  override def awaitReady(): Unit = {
    await condition isLoaded
    await spinner "Checking permissions..."
  }

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/method-configs/$methodConfigNamespace/$methodConfigName"

  private val openLaunchAnalysisModalButton = Button("open-launch-analysis-modal-button")
  private val openEditModeButton = Button("edit-method-config-button")
  private val editMethodConfigNameInput = TextField("edit-method-config-name-input")
  private val saveEditedMethodConfigButton = Button("save-edited-method-config-button")
  private val editMethodConfigSnapshotIdSelect = Select("edit-method-config-snapshot-id-select")
  private val editMethodConfigRootEntityTypeSelect = Select("edit-method-config-root-entity-type-select")
  private val deleteMethodConfigButton = Button("delete-method-config-button")
  private val modalConfirmDeleteButton = Button("modal-confirm-delete-button")
  private val snapshotRedactedLabel = Label("snapshot-redacted-title")

  def clickLaunchAnalysis(): Unit = {
    openLaunchAnalysisModalButton.doClick()
    // defensive code to prevents test from failing. after click, expect to find either a Message or Analysis Modal
    Try(
      await condition (find(CssSelectorQuery(".broadinstitute-modal-open")).nonEmpty, 5)
    ) match {
      case Failure(e) =>
        logger.warn(s"clickLaunchAnalysis Failed finding modal. Retrying click [button:${openLaunchAnalysisModalButton.query}]")
        openLaunchAnalysisModalButton.doClick()
      case Success(some) =>
        logger.info("clickLaunchAnalysis Success finding modal")
    }
  }

  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = "", enableCallCaching: Boolean = true): SubmissionDetailsPage = {
    val launchModal = openLaunchAnalysisModal()
    launchModal.launchAnalysis(rootEntityType, entityId, expression, enableCallCaching)
    await ready new SubmissionDetailsPage(namespace, name)
  }

  def openLaunchAnalysisModal(): LaunchAnalysisModal = {
    clickLaunchAnalysis()
    await ready new LaunchAnalysisModal
  }

  def clickLaunchAnalysisButtonError(): MessageModal = {
    clickLaunchAnalysis()
    await ready new MessageModal()
  }

  def openEditMode(expectSuccess: Boolean = true): Unit = {
    openEditModeButton.doClick()
    if (expectSuccess)
      saveEditedMethodConfigButton.awaitVisible()
  }

  def checkSaveButtonState(): String = {
    saveEditedMethodConfigButton.getState
  }

  def saveEdits(expectSuccess: Boolean = true): Unit = {
    // The button can sometimes scroll off the page and become unclickable. Therefore we need to scroll it into view.
    saveEditedMethodConfigButton.scrollToVisible()
    saveEditedMethodConfigButton.doClick()
    if (expectSuccess)
      openLaunchAnalysisModalButton.awaitVisible()
  }

  def editMethodConfig(newName: Option[String] = None, newSnapshotId: Option[Int] = None, newRootEntityType: Option[String] = None,
                       inputs: Option[Map[String, String]] = None, outputs: Option[Map[String, String]] = None): Unit = {
    openEditMode()
    await spinner "Loading attributes..."

    if (newName.isDefined) { editMethodConfigNameInput.setText(newName.get) }
    if (newSnapshotId.isDefined) { changeSnapshotId(newSnapshotId.get) }
    if (newRootEntityType.isDefined) { editMethodConfigRootEntityTypeSelect.select(newRootEntityType.get)}
    if (inputs.isDefined) { changeInputsOutputs(inputs.get) }
    if (outputs.isDefined) { changeInputsOutputs(outputs.get)}

    saveEdits()
  }

  def changeSnapshotId(newSnapshotId: Int): Unit = {
    editMethodConfigSnapshotIdSelect.select(newSnapshotId.toString)
    await spinner "Updating..."
  }

  private def changeInputsOutputs(fields: Map[String, String]): Unit = {
    for ((field, expression) <- fields) {
      val fieldInputQuery: Query = xpath(s"//*[@data-test-id='$field-text-input']/..//input")
      searchField(fieldInputQuery).value = expression
    }
  }

  // TODO This is a very weak check to deterimine if page is ready
  def isLoaded: Boolean = {
    openLaunchAnalysisModalButton.isVisible
  }

  def deleteMethodConfig(): WorkspaceMethodConfigListPage = {
    deleteMethodConfigButton.doClick()
    // TODO: make this a proper modal view
    modalConfirmDeleteButton.awaitVisible()
    modalConfirmDeleteButton.doClick()
    await ready new WorkspaceMethodConfigListPage(namespace, name)
  }

  def isSnapshotRedacted: Boolean = {
    snapshotRedactedLabel.isVisible
  }
}


/**
  * Page class for the launch analysis modal.
  */
class LaunchAnalysisModal(implicit webDriver: WebDriver) extends OKCancelModal("launch-analysis-modal") {
  override def awaitReady(): Unit = entityTable.awaitReady()

  private val entityTable = Table("entity-table" inside this)
  private val expressionInput = TextField("define-expression-input" inside this)
  private val noRowsMessage = Label("message-well" inside this)
  private val launchAnalysisButton = Button("launch-button" inside this)
  private val numberOfWorkflowsWarning = Label("number-of-workflows-warning" inside this)
  private val callCachingCheckbox = Checkbox("call-cache-checkbox" inside this)

  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = "", enableCallCaching: Boolean): Unit = { //Use Option(String) for expression?
    filterRootEntityType(rootEntityType)
    searchAndSelectEntity(entityId)
    if (!expression.isEmpty) { fillExpressionField(expression) }
    if (!enableCallCaching) { callCachingCheckbox.ensureChecked() }
    clickLaunchButton()
  }

  def validateLocation: Boolean = {
    entityTable.isVisible
  }

  def filterRootEntityType(rootEntityType: String): Unit = {
    entityTable.goToTab(rootEntityType)
  }

  def searchAndSelectEntity(entityId: String): Unit = {
    entityTable.filter(entityId)
    Link(entityId + "-link" inside entityTable).doClick()
  }

  def fillExpressionField(expression: String): Unit = {
    expressionInput.setText(expression)
  }

  def clickLaunchButton(): Unit = {
    launchAnalysisButton.doClick()
  }

  def verifyNoRowsMessage(): Boolean = {
    noRowsMessage.isVisible
  }

  def verifyWorkflowsWarning(): Boolean = {
    numberOfWorkflowsWarning.isVisible
  }

  def verifyErrorText(errorText: String): Boolean = {
    isErrorTextPresent(errorText)
  }

  private def isErrorTextPresent(errorText: String): Boolean = {
    val errorTextQuery: Query = text(errorText)
    await enabled errorTextQuery
    val error = find(errorTextQuery)
    error.size == 1
  }
}





