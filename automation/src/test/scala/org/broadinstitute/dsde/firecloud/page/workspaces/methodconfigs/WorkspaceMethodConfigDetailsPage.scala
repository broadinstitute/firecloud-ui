package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.SubmissionDetailsPage
import org.broadinstitute.dsde.firecloud.page.{ErrorModal, OKCancelModal, PageUtil}
import org.openqa.selenium.{JavascriptExecutor, WebDriver}
import org.scalatest.selenium.Page

class WorkspaceMethodConfigDetailsPage(namespace: String, name: String, methodConfigNamespace: String, methodConfigName: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceMethodConfigDetailsPage] {

  override def awaitReady(): Unit = {
    await condition isLoaded
    await spinner "Checking permissions..."
  }

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/method-configs/$methodConfigNamespace/$methodConfigName"

  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = "", enableCallCaching: Boolean = true): SubmissionDetailsPage = {
    val launchModal = ui.openLaunchAnalysisModal()
    launchModal.launchAnalysis(rootEntityType, entityId, expression, enableCallCaching)
    await ready new SubmissionDetailsPage(namespace, name)
  }

  def editMethodConfig(newName: Option[String] = None, newSnapshotId: Option[Int] = None, newRootEntityType: Option[String] = None,
                       inputs: Option[Map[String, String]] = None, outputs: Option[Map[String, String]] = None): Unit = {
    ui.openEditMode()
    await spinner "Loading attributes..."

    if (newName.isDefined) { ui.changeMethodConfigName(newName.get) }
    if (newSnapshotId.isDefined) { ui.changeSnapshotId(newSnapshotId.get) }
    if (newRootEntityType.isDefined) { ui.changeRootEntityType(newRootEntityType.get)}
    if (inputs.isDefined) { ui.changeInputsOutputs(inputs.get) }
    if (outputs.isDefined) { ui.changeInputsOutputs(outputs.get)}
    ui.clickSaveButton()

  }

  def openLaunchModal(): LaunchAnalysisModal = {
    ui.openLaunchAnalysisModal()
  }

  def isLoaded: Boolean = {
    ui.isLaunchAnalysisButtonPresent
  }

  def deleteMethodConfig(): WorkspaceMethodConfigListPage = {
    ui.deleteMethodConfig()
    new WorkspaceMethodConfigListPage(namespace, name)
  }

  trait UI extends super.UI {
    private val methodConfigNameTextQuery: Query = testId("method-config-name")
    private val openLaunchAnalysisModalButtonQuery: Query = testId("open-launch-analysis-modal-button")
    private val openEditModeQuery: Query = testId("edit-method-config-button")
    private val editMethodConfigNameInputQuery: Query = testId("edit-method-config-name-input")
    private val saveEditedMethodConfigButtonQuery: Query = testId("save-edited-method-config-button")
    private val cancelEditMethodConfigModeButtonQuery: Query = testId("cancel-edit-method-config-button")
    private val editMethodConfigSnapshotIdSelectQuery: Query = testId("edit-method-config-snapshot-id-select")
    private val editMethodConfigRootEntityTypeInputQuery: Query = testId("edit-method-config-root-entity-type-select")
    private val deleteMethodConfigButtonQuery: Query = testId("delete-method-config-button")
    private val modalConfirmDeleteButtonQuery: Query = testId("modal-confirm-delete-button")
    private val snapshotRedactedTitleQuery: Query = testId("snapshot-redacted-title")
    private val snapshotIdLabelQuery: Query = testId("method-label-Snapshot ID")

    def openLaunchAnalysisModal(): LaunchAnalysisModal = {
      await enabled methodConfigNameTextQuery
      click on (await enabled openLaunchAnalysisModalButtonQuery)
      await ready new LaunchAnalysisModal
    }

    def openEditMode(): Unit = {
      click on (await enabled openEditModeQuery)
    }

    def changeMethodConfigName(newName: String): Unit = {
      await enabled editMethodConfigNameInputQuery
      textField(editMethodConfigNameInputQuery).value = newName
    }

    def changeSnapshotId(newSnapshotId: Int): Unit = {
      await enabled editMethodConfigSnapshotIdSelectQuery
      singleSel(editMethodConfigSnapshotIdSelectQuery).value = newSnapshotId.toString
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
      await enabled snapshotIdLabelQuery
      find(snapshotRedactedTitleQuery).isDefined
    }

    def deleteMethodConfig(): Unit = {
      click on (await enabled deleteMethodConfigButtonQuery)
      click on (await enabled modalConfirmDeleteButtonQuery)
    }

  }
  object ui extends UI

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





