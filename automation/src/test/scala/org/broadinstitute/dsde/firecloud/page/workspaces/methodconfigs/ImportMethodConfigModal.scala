package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.MethodTable
import org.openqa.selenium.WebDriver

class ImportMethodConfigModal(implicit webDriver: WebDriver) extends Modal("import-method-configuration-modal") {
  private val chooseConfigFromRepoButton = Button("import-from-repo-button" inside this)
  private val copyConfigFromWorkspaceButton = Button("copy-from-workspace-button" inside this)

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
    await ready new SelectConfigurationView()
  }
}

private class SelectConfigurationView(implicit webDriver: WebDriver) extends FireCloudView {
  private val configTable = Table("config-table")
  private val useSelectedButton = Button("use-selected-configuration-button")
  private def configLink(namespace: String, name: String, snapshotId: Int) = Link(s"$namespace-$name-$snapshotId-link")

  override def awaitReady(): Unit = configTable.awaitReady()

  def selectConfiguration(namespace: String, name: String, snapshotId: Int): ConfirmMethodRepoImportView = {
    configTable.filter(name)
    configLink(namespace, name, snapshotId).doClick()
    useSelectedButton.awaitEnabled()
    useSelectedButton.doClick()
    await ready new ConfirmMethodRepoImportView()
  }
}

private class ConfirmMethodRepoImportView(implicit webDriver: WebDriver) extends FireCloudView {
  private val importMethodButton = Button("import-method-button")

  override def awaitReady(): Unit = importMethodButton.awaitVisible()

  def confirm(): Unit = {
    importMethodButton.doClick()
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

private class ConfirmConfigCopyView(implicit webDriver: WebDriver) extends FireCloudView {
  private val namespaceField = TextField("namespace-field")
  private val nameField = TextField("name-field")
  private val importButton = Button("import-button")

  override def awaitReady(): Unit = importButton.awaitEnabled()

  def importAsIs(): Unit = importAs()

  def importAs(namespace: Option[String] = None, name: Option[String] = None): Unit = {
    if (namespace.isDefined)
      namespaceField.setText(namespace.get)
    if (name.isDefined)
      nameField.setText(name.get)

    importButton.doClick()
  }
}
