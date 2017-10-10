package org.broadinstitute.dsde.firecloud.page.workspaces.monitor

import org.broadinstitute.dsde.firecloud.config.Config
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class SubmissionDetailsPage(namespace: String, name: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[SubmissionDetailsPage] {

  private val submissionId = getSubmissionId()
  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/monitor/$submissionId"

  private val WAITING_STATES = Array("Queued","Launching")
  private val WORKING_STATES = Array("Submitted", "Running", "Aborting")
  val SUCCESS_STATUS = "Succeeded"
  private val FAILED_STATUS  = "Failed"
  private val ABORTED_STATUS  = "Aborted"

  private val SUBMISSION_COMPLETE_STATES = Array("Done", SUCCESS_STATUS, FAILED_STATUS, ABORTED_STATUS)

  def isSubmissionDone():Boolean = {
    val status = ui.getSubmissionStatus()
    (SUBMISSION_COMPLETE_STATES.contains(status))
  }

  def getSubmissionId(): String = {
    ui.getSubmissionId()
  }

  def readWorkflowStatus() :String = {
    ui.getWorkflowStatus()
  }

  def verifyWorkflowSucceeded(): Boolean = {
    SUCCESS_STATUS == ui.getWorkflowStatus()
  }

  def verifyWorkflowFailed(): Boolean = {
    FAILED_STATUS == ui.getWorkflowStatus()
  }

  def verifyWorkflowAborted(): Boolean = {
    ABORTED_STATUS == ui.getWorkflowStatus()
  }

  def waitUntilSubmissionCompletes() = {
    while (!isSubmissionDone()) {
      open
    }
  }

  def abortSubmission() = {
    ui.abortSubmission()
  }

  trait UI extends super.UI {
    private val submissionStatusQuery: Query = testId("submission-status")
    private val workflowStatusQuery: Query = testId("workflow-status")
    private val submissionIdQuery: Query = testId("submission-id")
    private val submissionAbortButtonQuery: Query = testId("submission-abort-button")
    private val submissionAbortModalConfirmButtonQuery: Query = testId("submission-abort-modal-confirm-button")

    def getSubmissionStatus(): String = {
      (await enabled submissionStatusQuery).text
    }

    //This currently only works for 1 workflow!!!
    def getWorkflowStatus(): String = {
      await enabled workflowStatusQuery
      val workflowStatusElement = find(workflowStatusQuery)
      workflowStatusElement.get.text
    }

    def getSubmissionId(): String = {
      await enabled submissionIdQuery
      val submissionIdElement = find(submissionIdQuery)
      submissionIdElement.get.text
    }

    def abortSubmission() = {
      await enabled submissionAbortButtonQuery
      click on submissionAbortButtonQuery
      click on submissionAbortModalConfirmButtonQuery
    }
  }
  object ui extends UI
}
