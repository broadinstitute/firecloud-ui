package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.SubmissionDetailsPage
import org.broadinstitute.dsde.firecloud.page.{ErrorModal, OKCancelModal, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class WorkspaceMethodConfigDetailsPage(namespace: String, name: String, methodConfigNamespace: String, methodConfigName: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceMethodConfigDetailsPage] {

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

  def clickLaunchAnalysis(): Unit = openLaunchAnalysisModalButton.doClick()

  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = "", enableCallCaching: Boolean = true): SubmissionDetailsPage = {
    val launchModal = openLaunchAnalysisModal()
    launchModal.launchAnalysis(rootEntityType, entityId, expression, enableCallCaching)
    await ready new SubmissionDetailsPage(namespace, name)
  }

  def openLaunchAnalysisModal(): LaunchAnalysisModal = {
    openLaunchAnalysisModalButton.doClick()
    await ready new LaunchAnalysisModal
  }

  def clickLaunchAnalysisButtonError(): ErrorModal = {
    clickLaunchAnalysis()
    await ready new ErrorModal
  }

  def openEditMode(): Unit = {
    openEditModeButton.doClick()
    saveEditedMethodConfigButton.awaitVisible()
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
    if (newSnapshotId.isDefined) { editMethodConfigSnapshotIdSelect.select(newSnapshotId.get.toString) }
    if (newRootEntityType.isDefined) { editMethodConfigRootEntityTypeSelect.select(newRootEntityType.get)}
    if (inputs.isDefined) { changeInputsOutputs(inputs.get) }
    if (outputs.isDefined) { changeInputsOutputs(outputs.get)}

    saveEdits()
  }

  private def changeInputsOutputs(fields: Map[String, String]): Unit = {
    for ((field, expression) <- fields) {
      val fieldInputQuery: Query = xpath(s"//*[@data-test-id='$field-text-input']/..//input")
      searchField(fieldInputQuery).value = expression
    }
  }

  def openLaunchModal(): LaunchAnalysisModal = {
    openLaunchAnalysisModalButton.doClick()
    await ready new LaunchAnalysisModal
  }

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

    def changeSnapshotId(newSnapshotId: Int): Unit = {
      editMethodConfigSnapshotIdSelect.select(newSnapshotId.toString)
    }

    def changeRootEntityType(newRootEntityType: String): Unit = {
      await enabled editMethodConfigRootEntityTypeInputQuery
      singleSel(editMethodConfigRootEntityTypeInputQuery).value = newRootEntityType
    }

    def changeInputsOutputs(fields: Map[String, String]): Unit = {
      for ((field, expression) <- fields) {
        val fieldInputQuery: Query = xpath(s"//*[@data-test-id='$field-text-input']/..//input")
        searchField(fieldInputQuery).value = expression
      }
    }

    def checkSaveButtonState:String = {
      val button = await enabled saveEditedMethodConfigButtonQuery
      button.attribute("data-test-state").getOrElse("")
    }

    def clickSaveButton() = {
      val button = await enabled saveEditedMethodConfigButtonQuery
      // The button can sometimes scroll off the page and become unclickable. Therefore we need to scroll it into view.
      webDriver.asInstanceOf[JavascriptExecutor].executeScript("arguments[0].scrollIntoView(true)", button.underlying)
      click on button
    }

    def cancelEdits(): Unit = {
      click on (await enabled cancelEditMethodConfigModeButtonQuery)
    }

    def isLaunchAnalysisButtonPresent: Boolean = {
      await enabled openLaunchAnalysisModalButtonQuery
      find(openLaunchAnalysisModalButtonQuery).size == 1
    }

    def clickLaunchAnalysisButtonError(): ErrorModal = {
      click on (await enabled openLaunchAnalysisModalButtonQuery)
      new ErrorModal
    }

    def verifyMethodConfigurationName(methodConfigName: String): Boolean = {
      await enabled methodConfigNameTextQuery

      val methodConfigNameElement = find(methodConfigNameTextQuery)
      methodConfigNameElement.get.text == methodConfigName

    }

  def isSnapshotRedacted: Boolean = {
    snapshotRedactedLabel.isVisible
  }
}


/**
  * Page class for the launch analysis modal.
  */
class LaunchAnalysisModal(implicit webDriver: WebDriver) extends OKCancelModal {
  override def awaitReady(): Unit = entityTable.awaitReady()

  private val entityTable = Table("entity-table")
  private val expressionInput = TextField("define-expression-input")
  private val emptyDefaultEntitiesMessage = Label("message-well")
  private val launchAnalysisButton = Button("launch-button")
  private val numberOfWorkflowsWarning = Label("number-of-workflows-warning")
  private val callCachingCheckbox = Checkbox("call-cache-checkbox")

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
    click on testId(entityId + "-link")
  }

  def fillExpressionField(expression: String): Unit = {
    expressionInput.setText(expression)
  }

  def clickLaunchButton(): Unit = {
    launchAnalysisButton.doClick()
  }

  def verifyNoDefaultEntityMessage(): Boolean = {
    emptyDefaultEntitiesMessage.isVisible
  }

  def verifyWorkflowsWarning(): Boolean = {
    numberOfWorkflowsWarning.isVisible
  }

  def verifyWrongEntityError(errorText: String): Boolean = {
    isErrorTextPresent(errorText)
  }

  def verifyMissingInputsError(errorText: String): Boolean = {
    isErrorTextPresent(errorText)
  }

  private def isErrorTextPresent(errorText: String): Boolean = {
    val errorTextQuery: Query = text(errorText)
    await enabled errorTextQuery
    val error = find(errorTextQuery)
    error.size == 1
  }
}





