package org.broadinstitute.dsde.firecloud.page.workspaces.monitor

import org.broadinstitute.dsde.firecloud.component.{Link, SearchField}
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page


class WorkspaceMonitorPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceMonitorPage] {

  override def awaitReady(): Unit = filterInput.awaitVisible()

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/monitor"

  // TODO: make this a Table
  private val filterInput = SearchField("filter-input")

  private def submissionLink(name: String) = Link(s"submission-$name")

  def filter(searchText: String): Unit = {
    filterInput.setText(searchText)
    pressKeys("\n")
  }

  def openSubmission(submissionId: String): Unit = {
    submissionLink(submissionId).doClick()
    await ready new SubmissionDetailsPage(namespace, name, submissionId)
  }
}
