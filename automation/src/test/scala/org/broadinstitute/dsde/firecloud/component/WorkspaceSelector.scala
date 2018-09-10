package org.broadinstitute.dsde.firecloud.component

import org.openqa.selenium.WebDriver

case class WorkspaceSelector()(implicit webDriver: WebDriver) extends Component(TestId("workspace-selector")) {
  override def awaitReady(): Unit = dropdown.awaitReady()

  private val dropdown = Select2("destination-workspace" inside this)

  private val workspaceNameField = TextField("workspace-name-input" inside this)
  private val billingProjectSelect = Select("billing-project-select" inside this)
  private val descriptionArea = TextArea("workspace-description-text-field" inside this)
  private val authDomainSelect = Select("workspace-auth-domain-select" inside this)

  def selectExisting(project: String, name: String): Unit = dropdown.select(s"$project/$name")

  // TODO: dedupe this and create/clone workspace
  def selectNew(project: String, name: String, description: String = "test automation workspace", authDomain: Set[String] = Set.empty): Unit = {
    dropdown.select("Create new workspace...")

    workspaceNameField.awaitVisible()
    await notVisible (cssSelector("[data-test-id=spinner]"), 60) // slow to load up billing project and AuthDomain groups

    workspaceNameField.setText(name)
    billingProjectSelect.select(project)
    descriptionArea.setText(description)
    authDomain foreach { authDomainSelect.select }
  }
}
