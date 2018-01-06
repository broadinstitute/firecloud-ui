package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.methodrepo.MethodRepoTable
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.firecloud.page._
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page


class WorkspaceMethodConfigListPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceMethodConfigListPage] {

  override def awaitReady(): Unit = methodConfigsTable.awaitReady()

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/method-configs"

  private val openImportConfigModalButton: Button = Button("import-config-button")
  private val methodConfigsTable = Table("method-configs-table")
  private def methodConfigLinkId(methodName: String) = Link(s"method-config-$methodName-link")

//To-Do: Make this accept method namespace and participant
  /**
    * Imports Methods and Method Configs from the Method Repo. Note that the rootEntityType is only
    * necessary for Methods, but not Method Configs
    */
  def importMethodConfigFromRepo(methodNamespace: String, methodName: String, snapshotId: Int,
                                 methodConfigName: String, rootEntityType: Option[String] = None, grantMethodPermission: Option[Boolean] = None): WorkspaceMethodConfigDetailsPage = {
    openImportConfigModalButton.doClick()
    val chooseSourceModal = await ready new ImportMethodChooseSourceModel()
    chooseSourceModal.chooseConfigFromRepo(methodNamespace, methodName, snapshotId, methodConfigName, rootEntityType, grantMethodPermission)
    await ready new WorkspaceMethodConfigDetailsPage(namespace, name, methodNamespace, methodConfigName)
  }

  def importConfigButtonEnabled(): Boolean = openImportConfigModalButton.isStateEnabled

  def hasConfig(name: String): Boolean = {
    filter(name)
    methodConfigLinkId(name).isVisible
  }

  def hasRedactedIcon(configName: String): Boolean = {
    filter(configName)
    find(className(s"fa-exclamation-triangle")).isDefined
  }

  def openMethodConfig(methodNamespace: String, methodName: String): WorkspaceMethodConfigDetailsPage = {
    filter(methodName)
    methodConfigLinkId(methodName).doClick()
    await ready new WorkspaceMethodConfigDetailsPage(namespace, name, methodNamespace, methodName)
  }

  private def filter(searchText: String): Unit = {
    methodConfigsTable.filter(searchText)
  }
}

class ImportMethodChooseSourceModel(implicit webDriver: WebDriver) extends FireCloudView {
  override def awaitReady(): Unit = chooseConfigFromRepoModalButton.awaitVisible()

  private val chooseConfigFromRepoModalButton = Button("import-from-repo-button")

  def chooseConfigFromRepo(methodNamespace: String, methodName: String, snapshotId: Int, methodConfigName: String, rootEntityType: Option[String], grantMethodPermission: Option[Boolean] = None): Unit = {
    chooseConfigFromRepoModalButton.doClick()
    val importModel = await ready new ImportMethodConfigModal()
    importModel.importMethodConfig(methodNamespace, methodName, snapshotId, methodConfigName, rootEntityType, grantMethodPermission)

  }
}

/**
  * Page class for the import method config modal.
  */
class ImportMethodConfigModal(implicit webDriver: WebDriver) extends OKCancelModal {
  override def awaitReady(): Unit = Unit//MethodRepoTable.awaitReady

  private val snapshotDropdown = Select("snapshot-dropdown")
  private val selectConfigButton = Button("select-configuration-button")
  private val useBlankConfigButton = Button("use-blank-configuration-button")
  private val methodConfigNameField = TextField("method-config-name-field")
  private val rootEntitySelect = Select("root-entity-select")
  private val importMethodButton = Button("import-method-button")

  def importMethodConfig(methodNamespace: String, methodName: String, snapshotId: Int, methodConfigName: String, rootEntityType: Option[String], grantMethodPermission: Option[Boolean]): Unit = {
    val methodTable = new MethodRepoTable
    methodTable.filter(methodName)
    methodTable.enterMethod(methodNamespace, methodName)
    snapshotDropdown.select(snapshotId.toString)
    selectConfigButton.doClick()
    useBlankConfigButton.doClick()
    methodConfigNameField.setText(methodConfigName)
    if (rootEntityType.isDefined) rootEntitySelect.select(rootEntityType.get)
    importMethodButton.doClick()
    val syncUnableModal = new SynchronizeMethodAccessUnableModal()
    if (syncUnableModal.validateLocation) {
      syncUnableModal.clickOk()
    } else {
      if (grantMethodPermission.isDefined) {
        val syncModal = new SynchronizeMethodAccessModal()
        if (syncModal.validateLocation) {
          grantMethodPermission match {
            case Some(true) => syncModal.clickOk()
            case _ => syncModal.clickCancel()
          }
        }
      }
    }

  }
}
