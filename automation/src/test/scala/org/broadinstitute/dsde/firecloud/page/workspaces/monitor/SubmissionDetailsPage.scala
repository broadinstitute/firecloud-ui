package org.broadinstitute.dsde.firecloud.page.workspaces.monitor

import org.broadinstitute.dsde.firecloud.component.{Button, Label}
import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
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
    submissionId = submissionIdLabel.getText
  }

  private val submissionStatusLabel = Label("submission-status")
  private val workflowStatusLabel = Label("workflow-status")
  private val submissionIdLabel = Label("submission-id")
  private val submissionAbortButton = Button("submission-abort-button")
  private val submissionAbortModalConfirmButton = Button("submission-abort-modal-confirm-button")

  private val WAITING_STATES = Array("Queued","Launching")
  private val WORKING_STATES = Array("Submitted", "Running", "Aborting")
  val SUCCESS_STATUS = "Succeeded"
  private val FAILED_STATUS  = "Failed"
  private val ABORTED_STATUS  = "Aborted"

  private val SUBMISSION_COMPLETE_STATES = Array("Done", SUCCESS_STATUS, FAILED_STATUS, ABORTED_STATUS)

  def isSubmissionDone: Boolean = {
    val status = submissionStatusLabel.getText
    SUBMISSION_COMPLETE_STATES.contains(status)
  }

  def getSubmissionId: String = {
    submissionIdLabel.getText
  }

  def readWorkflowStatus(): String = {
    workflowStatusLabel.getText
  }

  def verifyWorkflowSucceeded(): Boolean = {
    SUCCESS_STATUS.contains(workflowStatusLabel.getText)
  }

  def verifyWorkflowFailed(): Boolean = {
    FAILED_STATUS.contains(workflowStatusLabel.getText)
  }

  def verifyWorkflowAborted(): Boolean = {
    ABORTED_STATUS.contains(workflowStatusLabel.getText)
  }

  def waitUntilSubmissionCompletes(): Unit = {
    while (!isSubmissionDone) {
      Thread sleep 1000  // this is needed for tests to pass on alpha. if you want to change how this is done, make sure it passes on alpha env
      open
    }
  }

  def abortSubmission(): Unit = {
    submissionAbortButton.doClick()
    submissionAbortModalConfirmButton.doClick()
  }
}
