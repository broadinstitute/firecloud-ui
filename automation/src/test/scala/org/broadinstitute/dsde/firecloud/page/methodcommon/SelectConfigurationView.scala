package org.broadinstitute.dsde.firecloud.page.methodcommon

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.ImportMethodConfigModal
import org.openqa.selenium.WebDriver

class SelectConfigurationView(importing: Boolean)(implicit webDriver: WebDriver) extends ImportMethodConfigModal {
  private val configTable = Table("config-table" inside this)
  private val useBlankButton = Button("use-blank-configuration-button")
  private val useSelectedButton = Button("use-selected-configuration-button")
  private def configLink(namespace: String, name: String, snapshotId: Int) = Link(s"$namespace-$name-$snapshotId-link" inside this)
  private val css = CssSelectorQuery(s"${this.query.queryString} [data-test-state=ready][data-test-persistence-key]")

  override def awaitReady(): Unit = configTable.awaitReady()

  def useBlankConfiguration(): ConfirmMethodImportExportView = {
    useBlankButton.doClick()
    await ready new ConfirmMethodImportExportView(importing)
  }

  def selectConfiguration(namespace: String, name: String, snapshotId: Int): ConfirmMethodImportExportView = {
    configTable.filter(name)
    configLink(namespace, name, snapshotId).doClick()
    // Size of modal changes while loading configuration details. Details section (right side) displays 2 expandable tables: "Inputs" and "Outputs"
    // wait until all 3 tables exist and ready in Modal to determine readiness
    await condition(findAll(css).length == 3)
    useSelectedButton.awaitEnabledState()
    useSelectedButton.doClick()
    await ready new ConfirmMethodImportExportView(importing)
  }
}
