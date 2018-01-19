package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.{MethodTable, Modal}
import org.openqa.selenium.WebDriver

class ImportMethodConfigModal(implicit webDriver: WebDriver) extends Modal {
  private val chooseConfigFromRepoModalButton = Button("import-from-repo-button")
  // private val chooseConfigFromWorkspaceModalButton = Button("copy-from-workspace-button")

  def chooseConfigFromRepo(methodNamespace: String, methodName: String, snapshotId: Int, methodConfigName: String, rootEntityType: Option[String]): Unit = {
    chooseConfigFromRepoModalButton.doClick()
    val importView = await ready new ImportFromMethodRepoView()
    importView.select(methodNamespace, methodName, snapshotId, methodConfigName, rootEntityType)
  }
}

private class ImportFromMethodRepoView(implicit webDriver: WebDriver) extends FireCloudView {
  private val methodRepoTable = new MethodTable[MethodSummaryView]() {
    override protected def awaitInnerPage(namespace: String, name: String): MethodSummaryView = {
      await ready new MethodSummaryView
    }
  }

  override def awaitReady(): Unit = methodRepoTable.awaitReady()

  def select(methodNamespace: String, methodName: String, snapshotId: Int, methodConfigName: String, rootEntityType: Option[String]): Unit = {
    methodRepoTable
      .enterMethod(methodNamespace, methodName)
      .confirm()
      .selectConfiguration(methodNamespace, methodConfigName, snapshotId) // seems like the wrong way to supply namespace?
      .confirm()
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
    // TODO: await/return WorkspaceMethodConfigDetailsPage?
  }
}
