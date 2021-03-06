package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.MethodTable
import org.broadinstitute.dsde.firecloud.page.methodcommon.SelectConfigurationView
import org.openqa.selenium.WebDriver

class ImportMethodConfigModal(implicit webDriver: WebDriver) extends Modal("import-method-configuration-modal") {
  private val chooseConfigFromRepoButton = Button("import-from-repo-button" inside this)
  private val copyConfigFromWorkspaceButton = Button("copy-from-workspace-button" inside this)

  override def awaitReady(): Unit = chooseConfigFromRepoButton.awaitVisible()

  def chooseConfigFromRepo(methodNamespace: String, methodName: String, snapshotId: Int, methodConfigName: String, rootEntityType: Option[String]): Unit = {
    chooseConfigFromRepoButton.doClick()
    (await ready new SelectMethodView())
      .selectMethod(methodNamespace, methodName)
      .confirm()
      .selectConfiguration(methodNamespace, methodConfigName, snapshotId)
      .confirm()
  }

  def copyConfigFromWorkspace(workspaceNamespace: String, workspaceName: String, configName: String): Unit = {
    copyConfigFromWorkspaceButton.doClick()
    (await ready new ChooseWorkspaceView())
      .selectWorkspace(workspaceNamespace, workspaceName)
      .selectConfig(configName)
      .importAsIs()
  }
}

/////////////////////////////////
// Import from Method Repo views:

private class SelectMethodView(implicit webDriver: WebDriver) extends FireCloudView {
  private val methodRepoTable = new MethodTable[MethodSummaryView]() {
    override protected def awaitInnerPage(namespace: String, name: String): MethodSummaryView = {
      await ready new MethodSummaryView
    }
  }

  override def awaitReady(): Unit = methodRepoTable.awaitReady()

  def selectMethod(methodNamespace: String, methodName: String): MethodSummaryView = {
    methodRepoTable.enterMethod(methodNamespace, methodName)
  }
}

private class MethodSummaryView(implicit webDriver: WebDriver) extends FireCloudView {
  private val selectButton = Button("select-configuration-button")

  override def awaitReady(): Unit = selectButton.awaitVisible()

  def confirm(): SelectConfigurationView = {
    selectButton.doClick()
    await ready new SelectConfigurationView(importing = true)
  }
}


/////////////////////////////
// Copy from workspace views:

private class ChooseWorkspaceView(implicit webDriver: WebDriver) extends FireCloudView {
  private val workspaceTable = Table("workspace-selector-table")
  private def workspaceLink(namespace: String, name: String) = Link(s"$namespace-$name-link")

  override def awaitReady(): Unit = workspaceTable.awaitReady()

  def selectWorkspace(namespace: String, name: String): ChooseConfigView = {
    workspaceTable.filter(name)
    workspaceLink(namespace, name).doClick()
    await ready new ChooseConfigView()
  }
}

private class ChooseConfigView(implicit webDriver: WebDriver) extends FireCloudView {
  private val configsTable = Table("method-configs-import-table")
  private def configLink(name: String) = Link(s"$name-link")

  override def awaitReady(): Unit = configsTable.awaitReady()

  def selectConfig(name: String): ConfirmConfigCopyView = {
    configsTable.filter(name)
    configLink(name).doClick()
    await ready new ConfirmConfigCopyView()
  }
}

private class ConfirmConfigCopyView(implicit webDriver: WebDriver) extends Modal("import-method-configuration-modal") {
  private val namespaceField = TextField("namespace-field" inside this)
  private val nameField = TextField("name-field" inside this)
  private val importButton = Button("import-button" inside this)

  override def awaitReady(): Unit = importButton.awaitEnabledState()

  def importAsIs(): Unit = importAs()

  def importAs(namespace: Option[String] = None, name: Option[String] = None): Unit = {
    if (namespace.isDefined)
      namespaceField.setText(namespace.get)
    if (name.isDefined)
      nameField.setText(name.get)

    importButton.doClick()
    awaitDismissed()
  }
}
