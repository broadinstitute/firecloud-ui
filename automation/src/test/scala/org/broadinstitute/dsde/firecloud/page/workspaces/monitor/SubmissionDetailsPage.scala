package org.broadinstitute.dsde.firecloud.page.workspaces.monitor

import java.util.concurrent.TimeoutException

import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.workbench.config.Config
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.workbench.service.test.Awaiter
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry

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
    * Wait for Submission to complete. 15 seconds polling.
    *
    * @param timeOut: Time out. Default set 35.minutes
    */
  def waitUntilSubmissionCompletes(timeOut: FiniteDuration = 35.minutes): Unit = {
    logger.info(s"Waiting for Workflow Submission to complete in $timeOut with 15 seconds polling interval")
    Thread.sleep(30000) // 30 seconds pause before checking
    retry[Boolean](15.seconds, timeOut) ({
      // sometimes page auto reloads, displaying table "Workflow Detail". link submissionId is not in table "Workflow Detail".
      // click tab "Monitor" loads either table "Workflow Details" or table "Analysis Detail".
      if (workflowStatusLabel.isVisible) {
        val monitorTab = goToMonitorTab()
        monitorTab.openSubmission(submissionId) // link exists in "Analysis Detail" table
        await ready (submissionStatusLabel, 5)
      }
      if (isError) {
        Some(false)
      } else {
        if (isSubmissionDone) Some(true) else None
      }
    }) match {
      case None => throw new TimeoutException(s"Timed out ($timeOut) waiting for Workflow submission: $submissionId to finish")
      case Some(false) => throw new Exception("Error on Submission page")
      case Some(true) => logger.info(s"Workflow Submission: $submissionId finished with status: ${getSubmissionStatus}")
    }
  }

  def abortSubmission(): Unit = {
    submissionAbortButton.doClick()
    val modal = await ready new ConfirmAbortModal()
    modal.clickAbortSubmissionButton
  }

}

class ConfirmAbortModal(implicit webDriver: WebDriver) extends ConfirmModal {

  val submissionAbortModalConfirmButton = Button("submission-abort-modal-confirm-button" inside this)
  override val readyComponent: Awaiter = submissionAbortModalConfirmButton

  override def awaitReady(): Unit = {
    super.awaitReady()
    submissionAbortModalConfirmButton.awaitEnabledState()
  }

  def clickAbortSubmissionButton: Unit = {
    await condition (getMessageText.equalsIgnoreCase("Are you sure you want to abort this submission?"), 10)
    submissionAbortModalConfirmButton.doClick()
    awaitDismissed()
  }

}
