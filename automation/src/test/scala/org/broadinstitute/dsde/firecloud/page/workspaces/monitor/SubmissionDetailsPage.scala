package org.broadinstitute.dsde.firecloud.page.workspaces.monitor

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page
import scala.concurrent.duration.DurationLong

class SubmissionDetailsPage(namespace: String, name: String, var submissionId: String = "unspecified")(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[SubmissionDetailsPage] {

  // TODO: Launch Analysis sends us to this page without knowing the submission ID. Fix this.
  override lazy val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/monitor/$submissionId"

  override def awaitReady(): Unit = {
    // TODO: wait on the table, once we're testing that
    submissionIdLabel.awaitVisible()
    workflowStatusLabel.awaitVisible()
    submissionId = submissionIdLabel.getText
  }

  private val submissionStatusLabel = Label("submission-status")
  private val workflowStatusLabel = Label("workflow-status")
  private val submissionIdLabel = Label("submission-id")
  private val submissionAbortButton = Button("submission-abort-button")
  private val submissionAbortModalConfirmButton = Button("submission-abort-modal-confirm-button")
  private val statusMessage = Label("status-message")

  private val WAITING_STATES = Array("Queued","Launching")
  private val WORKING_STATES = Array("Submitted", "Running", "Aborting")
  val SUCCESS_STATUS = "Succeeded"
  val FAILED_STATUS  = "Failed"
  val ABORTED_STATUS  = "Aborted"

  private val SUBMISSION_COMPLETE_STATES = Array("Done", SUCCESS_STATUS, FAILED_STATUS, ABORTED_STATUS)

  def getSubmissionStatus: String = {
    submissionStatusLabel.getText
  }

  def isSubmissionDone: Boolean = {
    SUBMISSION_COMPLETE_STATES.contains(getSubmissionStatus)
  }

  def getSubmissionId: String = {
    submissionIdLabel.getText
  }

  def readWorkflowStatus(): String = {
    workflowStatusLabel.getText
  }

  def readStatusMessage(): String = {
    statusMessage.getText
  }

  def verifyWorkflowSucceeded(): Boolean = {
    SUCCESS_STATUS.contains(readWorkflowStatus())
  }

  def verifyWorkflowFailed(): Boolean = {
    FAILED_STATUS.contains(readWorkflowStatus())
  }

  def verifyWorkflowAborted(): Boolean = {
    ABORTED_STATUS.contains(readWorkflowStatus())
  }

  /**
    * Wait maximum 5 minutes for Submission to complete.
    * Polling 10 seconds interval
    */
  def waitUntilSubmissionCompletes(): Unit = {
    retry[Boolean](10.seconds, 5.minutes)({
      if (isError) None // found error stops retry
      val monitorPage = goToMonitorTab()
      monitorPage.openSubmission(submissionId)
      if (isSubmissionDone) Some(true) else None
    }) match {
      case None => throw new Exception("Workflow Submission failed to complete")
      case Some(s) =>
    }
  }

  def abortSubmission(): Unit = {
    submissionAbortButton.doClick()
    submissionAbortModalConfirmButton.doClick()
  }
}
