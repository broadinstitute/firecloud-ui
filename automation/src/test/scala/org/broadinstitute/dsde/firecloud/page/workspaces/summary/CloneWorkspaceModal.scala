package org.broadinstitute.dsde.firecloud.page.workspaces.summary

import org.broadinstitute.dsde.firecloud.component._
import org.openqa.selenium.WebDriver

class CloneWorkspaceModal(implicit webDriver: WebDriver) extends OKCancelModal("clone-workspace-modal") {
  private val authDomainSelect = Select("workspace-auth-domain-select" inside this)
  private val billingProjectSelect = Select("billing-project-select" inside this)
  private val authDomainGroupsQuery: Query = testId("selected-auth-domain-group")
  private val workspaceNameInput = TextField("workspace-name-input" inside this)

  override def awaitReady(): Unit = billingProjectSelect.awaitVisible()

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
    // FIXME: Clone occasionally takes a long time, fix the root cause and revert this
    // MattP says (5/23/2018): If clone is taking forever, then that happens during the
    // submit() above, not on waiting for the new summary page to load below.
    await.ready(new WorkspaceSummaryPage(billingProjectName, workspaceName))
    //await ready new WorkspaceSummaryPage(billingProjectName, workspaceName)
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
