package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.openqa.selenium.WebDriver

class CloneWorkspaceModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    * Clones a new workspace. Returns immediately after submitting. Call awaitCloneComplete to wait for cloning to be done.
    *
    * @param workspaceName the name for the new workspace
    * @param billingProjectName the billing project for the workspace
    */
  def cloneWorkspace(billingProjectName: String, workspaceName: String, authDomain: Set[String] = Set.empty): Unit = {
    ui.selectBillingProject(billingProjectName)
    ui.fillWorkspaceName(workspaceName)
    authDomain foreach { ui.selectAuthDomain(_) }

    ui.clickCloneButton()
  }

  def cloneWorkspaceWait(): Unit = {
    // Micro-sleep to make sure the spinner has had a chance to render
    Thread sleep 200
    await notVisible spinner
  }


  object ui {
    private val authDomainSelect = testId("workspace-auth-domain-select")
    private val billingProjectSelect = testId("billing-project-select")
    private val cloneButtonQuery: Query = testId("create-workspace-button")
    private val authDomainGroupsQuery: Query = testId("selected-auth-domain-group")
    private val workspaceNameInput: Query = testId("workspace-name-input")

    def clickCloneButton(): Unit = {
      click on (await enabled cloneButtonQuery)
    }

    def fillWorkspaceName(name: String): Unit = {
      textField(workspaceNameInput).value = name
    }

    def readAuthDomainGroups(): List[(String, Boolean)] = {
      await visible authDomainGroupsQuery

      findAll(authDomainGroupsQuery).map { element =>
        (element.attribute("value").get, element.isEnabled)
      }.toList
    }

    def readLockedAuthDomainGroups(): List[String] = {
      readAuthDomainGroups().filterNot{ case (_, isEnabled) => isEnabled }.map { case (name, _) => name }
    }

    def selectAuthDomain(authDomain: String): Unit = {
      singleSel(authDomainSelect).value = option value authDomain
    }

    def selectBillingProject(billingProjectName: String): Unit = {
      singleSel(billingProjectSelect).value = option value billingProjectName
    }
  }
}
