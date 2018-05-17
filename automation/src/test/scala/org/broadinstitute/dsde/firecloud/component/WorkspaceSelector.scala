package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

class WorkspaceSelector(implicit webDriver: WebDriver) extends Component(TestId("workspace-selector")) {
  override def awaitReady(): Unit = dropdown.awaitReady()

  private val dropdown = Select2("destination-workspace" inside this)

  private val workspaceNameField = TextField("workspace-name-input" inside this)
  private val billingProjectSelect = Select("billing-project-select" inside this)
  private val descriptionArea = TextArea("workspace-description-text-field" inside this)
  private val authDomainSelect = Select("workspace-auth-domain-select" inside this)

  def selectExisting(project: String, name: String): Unit = dropdown.select(s"$project/$name")

  // TODO: dedupe this and create/clone workspace
  def selectNew(project: String, name: String, description: String = "", authDomain: Set[String] = Set.empty): Unit = {
    dropdown.select("Create new workspace...")

    workspaceNameField.awaitVisible()

    workspaceNameField.setText(name)
    billingProjectSelect.select(project)
    descriptionArea.setText(description)
    authDomain foreach { authDomainSelect.select }
  }
}
