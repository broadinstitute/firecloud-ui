package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.component.{Select, TextField}
import org.broadinstitute.dsde.firecloud.page.OKCancelModal
import org.openqa.selenium.WebDriver

class CloneWorkspaceModal(implicit webDriver: WebDriver) extends OKCancelModal {
  private val authDomainSelect = Select("workspace-auth-domain-select")
  private val billingProjectSelect = Select("billing-project-select")
  private val authDomainGroupsQuery: Query = testId("selected-auth-domain-group")
  private val workspaceNameInput = TextField("workspace-name-input")

  /**
    * Clones a new workspace.
    *
    * @param workspaceName the name for the new workspace
    * @param billingProjectName the billing project for the workspace
    */
  def cloneWorkspace(billingProjectName: String, workspaceName: String, authDomain: Set[String] = Set.empty): WorkspaceSummaryPage = {
    billingProjectSelect.select(billingProjectName)
    workspaceNameInput.setText(workspaceName)
    authDomain foreach { authDomainSelect.select }

    submit()
    await ready new WorkspaceSummaryPage(billingProjectName, workspaceName)
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
}
