package org.broadinstitute.dsde.firecloud.page.methodcommon

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.ImportMethodConfigModal
import org.openqa.selenium.WebDriver

class SelectConfigurationView(importing: Boolean)(implicit webDriver: WebDriver) extends ImportMethodConfigModal {
  private val configTable = Table("config-table" inside this)
  private val useBlankButton = Button("use-blank-configuration-button" inside this)
  private val useSelectedButton = Button("use-selected-configuration-button" inside this)
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
    await condition(findAll(css).length == 3) // after select, expect 3 tables with ready state
    useSelectedButton.awaitEnabledState()
    useSelectedButton.doClick()
    await ready new ConfirmMethodImportExportView(importing)
  }
}
