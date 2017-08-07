package org.broadinstitute.dsde.firecloud.page.workspaces

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.{FireCloudView, PageUtil}
import org.broadinstitute.dsde.firecloud.page.methods.MethodConfigDetailsPage
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page


class WorkspaceMethodConfigPage(namespace: String, name: String)(implicit webDriver: WebDriver) extends WorkspacePage with Page with PageUtil[WorkspaceMethodConfigPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/method-configs"

//To-Do: Make this accept method namespace and participant
  /**
    * Imports Methods and Method Configs from the Method Repo. Note that the rootEntityType is only
    * necessary for Methods, but not Method Configs
    */
  def importMethodConfigFromRepo(methodNamespace: String, methodName: String, snapshotId: Int, methodConfigName: String, rootEntityType: Option[String] = None): MethodConfigDetailsPage = {
    val chooseSourceModal = ui.clickImportConfigButton()
    chooseSourceModal.chooseConfigFromRepo(methodNamespace, methodName, snapshotId, methodConfigName, rootEntityType)
    new MethodConfigDetailsPage(namespace, name, methodNamespace, methodConfigName)
  }

  def filter(searchText: String): Unit = {
    ui.filter(searchText)
  }

  def openMethodConfig(methodNamespace: String, methodName: String): MethodConfigDetailsPage = {
    ui.openMethodConfig(methodName)
    new MethodConfigDetailsPage(namespace, name, methodNamespace, methodName)
  }

//  def is_method_config_present

  trait UI extends super.UI {
    private val openImportConfigModalButtonQuery: Query = testId("import-config-button")
    private val filterInput = testId("-input")
    private val methodConfigLinkId = "method-config-%s-link"

    def clickImportConfigButton(): ImportMethodChooseSourceModel = {
      click on (await enabled openImportConfigModalButtonQuery)
      new ImportMethodChooseSourceModel()
    }

    def filter(searchText: String) = {
      await enabled filterInput
      searchField(filterInput).value = searchText
      pressKeys("\n")
    }

    def openMethodConfig(methodName: String) = {
      val linkId = methodConfigLinkId.format(methodName)
      val link = testId(linkId)
      click on (await enabled link)
    }
  }
  object ui extends UI
}

class ImportMethodChooseSourceModel(implicit webDriver: WebDriver) extends FireCloudView {

  def chooseConfigFromRepo(methodNamespace: String, methodName: String, snapshotId: Int, methodConfigName: String, rootEntityType: Option[String]): Unit = {
    val importModel = gestures.clickChooseFromRepoButton()
    importModel.importMethodConfig(methodNamespace, methodName, snapshotId, methodConfigName, rootEntityType)
  }
  object gestures {
    private val chooseConfigFromRepoModalButtonQuery: Query = testId("import-from-repo-button")
    private val chooseConfigFromWorkspaceModalButtonQuery: Query = testId("copy-from-workspace-button")

    def clickChooseFromRepoButton(): ImportMethodConfigModal = {
      click on (await enabled chooseConfigFromRepoModalButtonQuery)
      new ImportMethodConfigModal()
    }
  }

}

/**
  * Page class for the import method config modal.
  */
class ImportMethodConfigModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    *
    */
  def importMethodConfig(methodNamespace: String, methodName: String, snapshotId: Int, methodConfigName: String, rootEntityType: Option[String]): Unit = {
    ui.searchMethodOrConfig(methodName)
    ui.selectMethodOrConfig(methodName, snapshotId)
    ui.fillNamespace(methodNamespace)
    ui.fillMethodConfigName(methodConfigName)
    if (rootEntityType != None) { ui.chooseRootEntityType(rootEntityType.get) }
    ui.clickimportMethodConfigButton()
  }

  object ui {

    private val methodSearchInputQuery: Query = testId("method-repo-table-input")
    private val methodNamespaceInputQuery: Query = testId("method-config-import-namespace-input")
    private val methodConfigNameInputQuery: Query = testId("method-config-import-name-input")
    private val importMethodConfigButtonQuery: Query = testId("import-button")
    private val rootEntityTypeSelectQuery: Query = testId("import-root-entity-type-select")

    def searchMethodOrConfig(searchQuery: String): Unit = {
      await enabled methodSearchInputQuery
        searchField(methodSearchInputQuery).value = searchQuery
      pressKeys("\n")
    }

    def selectMethodOrConfig(methodName: String, snapshotId: Int): Unit = {
      val methodLinkQuery: Query = testId(methodName + "_" + snapshotId) //TODO: update the testID to have a prefix for the import method configuration modal table row.... OR a <Namespace>-<name>_<snapshotid>
      click on testId(methodName + "_" + snapshotId)
    }

    def fillNamespace(methodNamespace: String): Unit = {
      await enabled methodNamespaceInputQuery
      textField(methodNamespaceInputQuery).value = methodNamespace
    }

    def fillMethodConfigName(methodConfigName: String): Unit = {
      await enabled methodConfigNameInputQuery
      textField(methodConfigNameInputQuery).value = methodConfigName
    }

    def chooseRootEntityType(rootEntityType: String) = {
      await enabled rootEntityTypeSelectQuery
      singleSel(rootEntityTypeSelectQuery).value = rootEntityType
    }


    def clickimportMethodConfigButton(): Unit = {
      click on (await enabled importMethodConfigButtonQuery)
    }

  }

}
