package org.broadinstitute.dsde.firecloud.page.workspaces.monitor

import org.broadinstitute.dsde.firecloud.{FireCloudConfig, FireCloudView}
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

import scala.util.{Failure, Success, Try}


class WorkspaceMonitorPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceMonitorPage] {

  override def awaitReady(): Unit = {
    if (!isError) filterInput.awaitVisible()
  }

  override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#workspaces/$namespace/$name/monitor"

  // TODO: make this a Table
  private val filterInput = SearchField("filter-input")

  private def submissionLink(name: String) = Link(s"submission-$name")

  def filter(searchText: String): Unit = {
    filterInput.setText(searchText)
    pressKeys("\n")
  }

  def openSubmission(submissionId: String): Unit = {
    if (Link("submission-id").isVisible)
      goToMonitorTab()
    clickSubmissionLink(submissionId, new SubmissionDetailsPage(namespace, name, submissionId))
  }

  private def clickSubmissionLink[T <: FireCloudView](submissionId: String, page: T): T = {
    submissionLink(submissionId).doClick()
    Try {
      await ready page
    } match {
      case Failure(e) => // click failed
        logger.warn(s"clickSubmissionLink Failure. Retrying click submission link: submission-$submissionId}")
        submissionLink(submissionId).doClick()
        await ready page
      case Success(some) => // clicked
        page
    }
  }

}
