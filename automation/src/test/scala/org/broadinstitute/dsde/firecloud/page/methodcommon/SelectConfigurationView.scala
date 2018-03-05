package org.broadinstitute.dsde.firecloud.page.methodcommon

import org.broadinstitute.dsde.firecloud.FireCloudView
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.component._
import org.openqa.selenium.WebDriver

class SelectConfigurationView(importing: Boolean)(implicit webDriver: WebDriver) extends FireCloudView {
  private val configTable = Table("config-table")
  private val useBlankButton = Button("use-blank-configuration-button")
  private val useSelectedButton = Button("use-selected-configuration-button")
  private def configLink(namespace: String, name: String, snapshotId: Int) = Link(s"$namespace-$name-$snapshotId-link")

  override def awaitReady(): Unit = configTable.awaitReady()

  def useBlankConfiguration(): ConfirmMethodImportExportView = {
    useBlankButton.doClick()
    await ready new ConfirmMethodImportExportView(importing)
  }

  def selectConfiguration(namespace: String, name: String, snapshotId: Int): ConfirmMethodImportExportView = {
    configTable.filter(name)
    configLink(namespace, name, snapshotId).doClick()
    useSelectedButton.awaitEnabledState()
    useSelectedButton.doClick()
    await ready new ConfirmMethodImportExportView(importing)
  }
}
