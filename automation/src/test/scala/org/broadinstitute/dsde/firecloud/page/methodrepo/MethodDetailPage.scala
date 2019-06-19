package org.broadinstitute.dsde.firecloud.page.methodrepo

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.methodcommon.SelectConfigurationView
import org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs.WorkspaceMethodConfigDetailsPage
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.openqa.selenium.WebDriver

class MethodDetailPage(namespace: String, name: String)(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with PageUtil[MethodDetailPage] {


  lazy override val url = s"${FireCloudConfig.FireCloud.baseUrl}#methods/$namespace/$name/${snapshotVersion}"

  private val exportButton = Button("export-to-workspace-button")

  private val redactButton = Button("redact-button")

  override def awaitReady(): Unit = {
    super.awaitReady()
    redactButton.awaitVisible()
  }

  def snapshotVersion: String = {
    val ver = CssSelectorQuery(s"[data-test-id=snapshot-dropdown] > span").element.underlying.getText
    ver
  }

  def startExport(): ExportModal = {
    exportButton.doClick()
    await ready new ExportModal(namespace, name)
  }

  def redact(): Unit = {
    redactButton.doClick()
    new OKCancelModal("confirm-redaction-modal").clickOk()
    // redact takes us back to the table:
    redactButton.awaitNotVisible()
  }
}

class ExportModal(methodNamespace: String, methodName: String)(implicit webDriver: WebDriver) extends Modal("export-config-to-workspace-modal") {
  val firstPage = new SelectConfigurationView(importing = false)

  def getPostExportModal: PostExportModal = await ready new PostExportModal(methodNamespace, methodName)
}

class PostExportModal(methodNamespace: String, methodName: String)(implicit webDriver: WebDriver) extends OKCancelModal("export-successful-modal") {
  def goToWorkspace(project: String, wsName: String): WorkspaceMethodConfigDetailsPage = {
    submit()
    await ready new WorkspaceMethodConfigDetailsPage(project, wsName, methodNamespace, methodName)
  }

  def stayHere(): Unit = {
    cancel()
  }
}