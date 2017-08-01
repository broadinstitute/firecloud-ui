package org.broadinstitute.dsde.firecloud.page.methods

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.workspaces.{SubmissionDetailsPage, WorkspacePage, WorkspaceMethodConfigPage}
import org.broadinstitute.dsde.firecloud.page.{FireCloudView, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodConfigDetailsPage(namespace: String, name: String, methodConfigNamespace: String, methodConfigName: String)(implicit webDriver: WebDriver) extends WorkspacePage with Page with PageUtil[MethodConfigDetailsPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/method-configs/$methodConfigNamespace/$methodConfigName"

  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = "", enableCallCaching: Boolean = true) = {
    val launchModal = ui.openLaunchAnalysisModal()
    launchModal.launchAnalysis(rootEntityType, entityId, expression, enableCallCaching)
    new SubmissionDetailsPage(namespace, name)
  }

  def editMethodConfig(newName: Option[String] = None, newSnapshotId: Option[Int] = None, newRootEntityType: Option[String] = None,
                       inputs: Option[Map[String, String]] = None, outputs: Option[Map[String, String]] = None) = {
    ui.openEditMode()
    if (newName != None) { ui.changeMethodConfigName(newName.get) }
    if (newSnapshotId != None) { ui.changeSnapshotId(newSnapshotId.get) }
    if (newRootEntityType != None) { ui.changeRootEntityType(newRootEntityType.get)}
    if (inputs != None) { ui.changeInputsOutputs(inputs.get)  }
    if (outputs != None) { ui.changeInputsOutputs(outputs.get)}
    ui.saveEdits()

  }

  def openlaunchModal() = {
    ui.openLaunchAnalysisModal()
  }

  def isLoaded: Boolean = {
    ui.isLaunchAnalysisButtonPresent()
  }

  override def awaitLoaded(): MethodConfigDetailsPage = {
    await condition isLoaded
    this
  }

  def deleteMethodConfig(): WorkspaceMethodConfigPage = {
    ui.deleteMethodConfig()
    new WorkspaceMethodConfigPage(namespace, name)
  }

  trait UI extends super.UI {
    private val methodConfigNameTextQuery: Query = testId("method-config-name")
    private val openLaunchAnalysisModalButtonQuery: Query = testId("open-launch-analysis-modal-button")
    private val openEditModeQuery: Query = testId("edit-method-config-button")
    private val editMethodConfigNameInputQuery: Query = testId("edit-method-config-name-input")
    private val saveEdittedMethodConfigButtonQuery: Query = testId("save-editted-method-config-button")
    private val cancelEditMethodConfigModeButtonQuery: Query = testId("cancel-edit-method-config-button")
    private val editMethodConfigSnapshotIdSelectQuery: Query = testId("edit-method-config-snapshot-id-select")
    private val editMethodConfigRootEntityTypeInputQuery: Query = testId("edit-method-config-root-entity-type-select")
    private val deleteMethodConfigButtonQuery: Query = testId("delete-method-config-button")
    private val modalConfirmDeleteButtonQuery: Query = testId("modal-confirm-delete-button")

    def openLaunchAnalysisModal(): LaunchAnalysisModal = {
      await enabled methodConfigNameTextQuery
      //await thenClick(openLaunchAnalysisModalButtonQuery)
      click on (await enabled openLaunchAnalysisModalButtonQuery)
      new LaunchAnalysisModal
    }

    def openEditMode() = {
      click on (await enabled openEditModeQuery)
    }

    def changeMethodConfigName(newName: String) = {
      await enabled editMethodConfigNameInputQuery
      textField(editMethodConfigNameInputQuery).value = newName
    }

    def changeSnapshotId(newSnapshotId: Int) = {
      await enabled editMethodConfigSnapshotIdSelectQuery
      singleSel(editMethodConfigSnapshotIdSelectQuery).value = newSnapshotId.toString
    }

    def changeRootEntityType(newRootEntityType: String) = {
      await enabled editMethodConfigRootEntityTypeInputQuery
      singleSel(editMethodConfigRootEntityTypeInputQuery).value = newRootEntityType
    }

    def changeInputsOutputs(fields: Map[String, String]) = {
      for ((field, expression) <- fields) {
        val fieldInputQuery: Query = testId(field + "-text-input")
        click on(await writable fieldInputQuery)
        enter(expression)
      }
    }

    def saveEdits() = {
      click on (await enabled saveEdittedMethodConfigButtonQuery)
    }

    def cancelEdits() = {
      click on (await enabled cancelEditMethodConfigModeButtonQuery)
    }

    def isLaunchAnalysisButtonPresent() = {
      await enabled openLaunchAnalysisModalButtonQuery
      find(openLaunchAnalysisModalButtonQuery).size == 1
    }

    def verifyMethodConfigurationName(methodConfigName: String) = {
      await enabled methodConfigNameTextQuery

      val methodConfigNameElement = find(methodConfigNameTextQuery)
      methodConfigNameElement.get.text == methodConfigName

    }

    def deleteMethodConfig() = {
      click on (await enabled deleteMethodConfigButtonQuery)
      click on (await enabled modalConfirmDeleteButtonQuery)
    }

  }
  object ui extends UI

}



/**
  * Page class for the launch analysis modal.
  */
class LaunchAnalysisModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    *
    */
  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = "", enableCallCaching: Boolean): Unit = { //Use Option(String) for expression?
    ui.filterRootEntityType(rootEntityType)
    ui.searchEntity(entityId)
    ui.selectEntity(entityId)
    if (!expression.isEmpty()) { ui.fillExpression(expression) }
    if (!enableCallCaching) { ui.clickCallCachingCheckbox() }
    ui.clickLaunchButton()
  }

  def filterRootEntityType(rootEntityType: String) = {
    ui.filterRootEntityType(rootEntityType)
  }

  def searchAndSelectEntity(entityId: String) = {
    ui.searchEntity(entityId)
    ui.selectEntity(entityId)
  }

  def fillExpressionField(expression: String) = {
    ui.fillExpression(expression)
  }

  def clickLaunchButton() = {
    ui.clickLaunchButton()
  }

  def verifyNoDefaultEntityMessage(): Boolean = {
    ui.isNoDefaultEntitiesMessagePresent()
  }

  def verifyWorkflowsWarning(): Boolean = {
    ui.isNumberOfWorkflowWarningPresent()
  }

  def verifyWrongEntityError(errorText: String): Boolean = {
    ui.isErrorTextPresent(errorText)
  }

  def verifyMissingInputsError(errorText: String): Boolean = {
    ui.isErrorTextPresent(errorText)
  }

  def closeModal() = {
    ui.closeModal()
  }

  object ui {
    private val participantRootEntityFilterButtonQuery: Query = testId("participant-filter-button")
    private val participantSetRootEntityFilterButtonQuery: Query = testId("participant_set-filter-button")
    private val entitySearchInputQuery: Query = testId("entity-table-input")
    private val expressionInputQuery: Query = testId("define-expression-input")
    private val emptyDefaultEntitiesMessageQuery: Query = testId("message-well")
    private val launchAnalysisButtonQuery: Query = testId("launch-button")
    private val closeModalXButtonQuery: Query = testId("x-button")
    private val numberOfWorkflowsWarningQuery: Query = testId("number-of-workflows-warning")
    private val callCachingCheckboxQuery: Query = testId("call-cache-checkbox")

    private val emptyDefaultMessage = "There are no entities to display."

    def filterRootEntityType(rootEntityType: String) = {
      click on (await enabled testId(rootEntityType + "-filter-button"))
    }

    def filterParticipantSetType() = {
      click on (await enabled participantSetRootEntityFilterButtonQuery)
    }

    def searchEntity(entityId: String) = {
      await enabled entitySearchInputQuery
      searchField(entitySearchInputQuery).value = entityId
      pressKeys("\n")
    }

    def selectEntity(entityId: String) = {
      await enabled launchAnalysisButtonQuery
      click on (await enabled text(entityId))
    }

    def fillExpression(expression: String) = {
      await enabled expressionInputQuery
      searchField(expressionInputQuery).value = expression
    }

    def clickLaunchButton() = {
      click on (await enabled launchAnalysisButtonQuery)
    }

    def isNoDefaultEntitiesMessagePresent(): Boolean = {
      await enabled emptyDefaultEntitiesMessageQuery
      find(emptyDefaultEntitiesMessageQuery).size == 1
    }

    def closeModal() = {
      click on (await enabled closeModalXButtonQuery)
    }

    def isNumberOfWorkflowWarningPresent(): Boolean = {
      await enabled numberOfWorkflowsWarningQuery
      find(numberOfWorkflowsWarningQuery).size == 1
    }

    def isErrorTextPresent(errorText: String): Boolean = {
      val errorTextQuery: Query = text(errorText)
      await enabled errorTextQuery
      val error = find(errorTextQuery)
      error.size == 1
    }

    def clickCallCachingCheckbox() = {
      click on (await enabled callCachingCheckboxQuery)
    }

  }
}





