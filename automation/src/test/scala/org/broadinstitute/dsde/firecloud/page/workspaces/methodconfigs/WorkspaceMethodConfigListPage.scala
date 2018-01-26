package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.workbench.config.Config
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
                                 methodConfigName: String, rootEntityType: Option[String] = None): WorkspaceMethodConfigDetailsPage = {
    openImportConfigModalButton.doClick()
    val importModal = await ready new ImportMethodConfigModal()
    importModal.chooseConfigFromRepo(methodNamespace, methodName, snapshotId, methodConfigName, rootEntityType)
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
