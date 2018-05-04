package org.broadinstitute.dsde.firecloud.page.billing

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.workbench.config.{Config, Credentials}
import org.broadinstitute.dsde.firecloud.page.{BaseFireCloudPage, PageUtil}
import org.broadinstitute.dsde.workbench.service.util.Retry.retry
import org.broadinstitute.dsde.workbench.service.util.Util
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

import scala.concurrent.duration.DurationLong

/**
  * Page class for managing billing projects.
  */
class BillingManagementPage(implicit webDriver: WebDriver) extends BaseFireCloudPage
  with Page with PageUtil[BillingManagementPage] with LazyLogging {

  override val url: String = s"${Config.FireCloud.baseUrl}#billing"

  override def awaitReady: Unit = {
    billingProjectTable.awaitReady()
  }

  private val billingProjectTable = Table("billing-project-table")

  private val createBillingProjectButton = Button("begin-create-billing-project")
  private val addUserButton = Button("billing-project-add-user-button")

  /**
    * Creates a new billing project. Returns after creation has started, though
    * it may not yet be complete. Creation status, including success or
    * failure, can be queried using ui.creationStatusForProject().
    *
    * @param projectName the name for the new project
    * @param billingAccountName the billing account for the new project
    */
  def createBillingProject(projectName: String, billingAccountName: String): Unit = {
    createBillingProjectButton.doClick()
    val modal = await ready new CreateBillingProjectModal
    modal.createBillingProject(projectName, billingAccountName)
  }

  /**
    * Waits until creation of a billing project is complete. The result can be
    * "success", "failure", or "unknown".
    *
    * Note: this has a side effect of filtering the billing project list in
    * order to make sure the status of the requested project is visible.
    *
    * @param projectName the billing project name
    * @return a status of "success", "failure", or "unknown"
    */
  def waitForCreateDone(projectName: String): Option[String] = {
    billingProjectTable.filter(projectName)
    retry(10.seconds, 10.minutes)({
      readCreationStatusForProject(projectName).filterNot(_ equals "running")
    })
  }

  private def readCreationStatusForProject(projectName: String): Option[String] = {
    for {
      e <- find(xpath(s"//div[@data-test-id='$projectName-row']//span[@data-test-id='status-icon']"))
      v <- e.attribute("data-test-value")
    } yield v
  }


  def openBillingProject(projectName: String): Unit = {
    billingProjectTable.filter(projectName)
    Link(projectName + "-link").doClick()
    addUserButton.awaitEnabledState()
  }


  def addUserToBillingProject(userEmail: String, role: String): Unit = {
    addUserButton.doClick()
    val modal = await ready new AddUserToBillingProjectModal
    modal.addUserToBillingProject(userEmail, role)
  }

  def isUserInBillingProject(userEmail: String): Boolean = {
    val emailQuery = testId(userEmail)
    await enabled emailQuery
    val userEmailElement = find(emailQuery)
    userEmail == userEmailElement.get.text
  }

  /**
    *
    * @param billingProjectName
    * @param user
    * @return Status of "success", "failure", or "unknown"
    */
  def newBillingProject(billingProjectName: String, user: Credentials): Option[String] = {
    val billingProjectName = "billing-" + Util.makeRandomId(8)

    logger.info(s"Creating billing project: $billingProjectName")

    createBillingProject(billingProjectName, Config.Projects.billingAccount)
    val statusOption: Option[String] = waitForCreateDone(billingProjectName)

    statusOption match {
      case None | Some("failure") | Some("unknown") =>
        logger.info(s"Failure or timeout creating billing project: $billingProjectName")
        statusOption
      case Some("success") =>
        logger.info(s"Created billing project: $billingProjectName")
    }
    statusOption
  }
}


/**
  * Page class for the modal for creating a billing project.
  */
class CreateBillingProjectModal(implicit webDriver: WebDriver) extends OKCancelModal("create-billing-project-modal") {
  override def awaitReady(): Unit = projectNameInput.awaitVisible()

  private val projectNameInput = TextField("project-name-input" inside this)
  private def billingAccountButton(name: String) = Link(name + "-radio" inside this)

  def createBillingProject(projectName: String, billingAccountName: String): Unit = {
    projectNameInput.setText(projectName)
    billingAccountButton(billingAccountName).doClick()
    submit()
    projectNameInput.awaitNotVisible(60)
  }
}


/**
  * Page class for the modal for adding users to a billing project.
  */
class AddUserToBillingProjectModal(implicit webDriver: WebDriver) extends OKCancelModal("add-user-to-billing-project-modal") {
  private val addUserModalEmailInput = TextField("billing-project-add-user-modal-user-email-input" inside this)
  private val addUserModalRoleSelect = Select("billing-project-add-user-modal-user-role-select" inside this)

  def addUserToBillingProject(userEmail: String, role: String): Unit = {
    addUserModalEmailInput.setText(userEmail)
    addUserModalRoleSelect.select(role)
    clickOk()
  }
}
