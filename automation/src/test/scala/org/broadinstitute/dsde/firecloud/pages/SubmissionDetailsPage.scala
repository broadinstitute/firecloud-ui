package org.broadinstitute.dsde.firecloud.pages

import org.broadinstitute.dsde.firecloud.{Config, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class SubmissionDetailsPage(namespace: String, name: String)(implicit webDriver: WebDriver) extends WorkspacePage with Page with PageUtil[SubmissionDetailsPage] {

  private val submissionId = getSubmissionId()
  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/monitor/$submissionId"

  private val WAITING_STATS = Array("Queued","Launching")
  private val WORKING_STATS = Array("Submitted", "Running", "Aborting")
  private val SUCCESS_STATS = Array("Succeeded")
  private val FAILED_STATS  = Array("Failed", "Aborted")

  private val SUBMISSION_COMPLETE_STATS = Array("Done")

  def isSubmissionDone():Boolean = {
    val status = ui.getSubmissionStatus()
    (SUBMISSION_COMPLETE_STATS.contains(status))
  }

  def getSubmissionId(): String = {
    ui.getSubmissionId()
  }

  def verifyWorkflowSucceeded(): Boolean = {
    val status = ui.getWorkflowStatus()
    SUCCESS_STATS.contains(status)
  }

  def verifyWorkflowFailed(): Boolean = {
    val status = ui.getWorkflowStatus()
    FAILED_STATS.contains(status)
  }

  def waitUntilSubmissionCompletes() = {
    while (!isSubmissionDone()) {
      open
    }
  }

  trait UI extends super.UI {
    private val submissionStatusQuery: Query = testId("submission-status")
    private val workflowStatusQuery: Query = testId("workflow-status")
    private val submissionIdQuery: Query = testId("submission-id")

    def getSubmissionStatus(): String = {
      await enabled submissionStatusQuery
      val submissionStatusElement = find(submissionStatusQuery)
      submissionStatusElement.get.text
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
  }
  object ui extends UI
}
