package org.broadinstitute.dsde.firecloud.page.workspaces.monitor

import java.util.concurrent.TimeoutException

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.service.Rawls
import org.broadinstitute.dsde.workbench.service.test.Awaiter
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry

import scala.util.{Failure, Success, Try}

class SubmissionDetailsPage(namespace: String, name: String, var submissionId: String = "unspecified")(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with PageUtil[SubmissionDetailsPage] {

  // TODO: Launch Analysis sends us to this page without knowing the submission ID. Fix this.
  override lazy val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#workspaces/$namespace/$name/monitor/$submissionId"

  override def awaitReady(): Unit = {
    // TODO: wait on the table, once we're testing that
    submissionIdLabel.awaitVisible()
    workflowStatusLabel.awaitVisible()
    submissionId = submissionIdLabel.getText
  }

  private val submissionStatusLabel = Label("submission-status")
  private val submissionIdLabel = Label("submission-id")
  private val submissionAbortButton = Button("submission-abort-button")
  private val statusMessage = Label("status-message")

  // if the submission has multiple workflows, there will be multiple statuses and ids on the page
  // and these labels will only refer to the one Selenium chooses by default.
  private val workflowStatusLabel = Label("workflow-status")
  private val workflowIdLabel = Label("workflow-id")

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

  // if the submission has multiple workflows, there will be multiple statuses on the page
  // and this method will only read the one Selenium chooses by default.
  def readWorkflowStatus(): String = {
    workflowStatusLabel.getText
  }

  // if the submission has multiple workflows, there will be multiple ids on the page
  // and this method will only read the one Selenium chooses by default.
  def readWorkflowId(): String = {
    Try(workflowIdLabel.getText) match {
      case Success(id) => id
      case Failure(ex) => s"workflow id unavailable: ${ex.getMessage}"
    }
  }

  def readStatusMessage(): String = {
    statusMessage.getText
  }

  def verifyWorkflowStatus(expectedStatus: String): Boolean = {
    val actualStatus = readWorkflowStatus()
    val pass = expectedStatus.contains(actualStatus)
    if (!pass) {
      val workflowId = readWorkflowId()
      logger.error(s"for workflow id [$workflowId[, expected status [$expectedStatus]; actually [$actualStatus]")
    }
    pass
  }

  def verifyWorkflowSucceeded(): Boolean = {
    verifyWorkflowStatus(SUCCESS_STATUS)
  }

  def verifyWorkflowFailed(): Boolean = {
    verifyWorkflowStatus(FAILED_STATUS)
  }

  def verifyWorkflowAborted(): Boolean = {
    verifyWorkflowStatus(ABORTED_STATUS)
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
        await ready (submissionStatusLabel)
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

  def abortButtonVisible: Boolean = submissionAbortButton.isVisible

  def getApiSubmissionStatus(billingProject: String, workspaceName: String, submissionId: String)(implicit token: AuthToken): String = {
    val (status, workflows) = Rawls.submissions.getSubmissionStatus(billingProject, workspaceName, submissionId)
    logger.info(s"Status is $status in Submission $billingProject/$workspaceName/$submissionId")
    status
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
