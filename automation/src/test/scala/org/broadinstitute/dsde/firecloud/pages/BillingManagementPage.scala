package org.broadinstitute.dsde.firecloud.pages

import org.broadinstitute.dsde.firecloud.Util.retry
import org.broadinstitute.dsde.firecloud.{Config, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

import scala.concurrent.duration.DurationLong

/**
  * Page class for managing billing projects.
  */
class BillingManagementPage(implicit webDriver: WebDriver) extends AuthenticatedPage
  with Page with PageUtil[BillingManagementPage] {
  override val url: String = s"${Config.FireCloud.baseUrl}#billing"

  override def awaitLoaded(): BillingManagementPage = {
//    await enabled testId("begin-create-billing-project")
    await condition ui.hasCreateBillingProjectButton
    this
  }

  /**
    * Creates a new billing project. Returns after creation has started, though
    * it may not yet be complete. Creation status, including success or
    * failure, can be queried using ui.creationStatusForProject().
    *
    * @param projectName the name for the new project
    * @param billingAccountName the billing account for the new project
    */
  def createBillingProject(projectName: String, billingAccountName: String): Unit = {
    val modal = ui.clickCreateBillingProjectButton()
    modal.createBillingProject(projectName, billingAccountName)
  }

  /**
    * Filters the list of billing projects.
    *
    * @param text the text to filter by
    */
  def filter(text: String): Unit = {
    ui.fillFilterText(text)
    ui.clickFilterButton()
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
  def waitForCreateCompleted(projectName: String): String = {
    filter(projectName)
    retry(Seq.fill(60)(10.seconds)) {
      ui.readCreationStatusForProject(projectName).filterNot(_ equals "running")
    } match {
      case None => throw new Exception("Billing project creation did not complete")
      case Some(s) => s
    }
  }


  trait UI extends super.UI {
    private val createBillingProjectButton: Query = testId("begin-create-billing-project")
    private val filterButton = testId("billing-project-list-filter-button")
    private val filterInput = testId("billing-project-list-filter-input")

    def clickCreateBillingProjectButton(): CreateBillingProjectModal = {
      click on createBillingProjectButton
      new CreateBillingProjectModal
    }

    def clickFilterButton(): Unit = {
      click on (await enabled filterButton)
    }

    def fillFilterText(text: String): Unit = {
      await enabled filterInput
      searchField(filterInput).value = text
    }

    def hasCreateBillingProjectButton: Boolean = {
      find(createBillingProjectButton).isDefined
    }

    def readCreationStatusForProject(projectName: String): Option[String] = {
      for {
        e <- find(xpath(s"//div[@data-test-id='$projectName-row']//span[@data-test-id='status-icon']"))
        v <- e.attribute("data-test-value")
      } yield v
    }
  }
  object ui extends UI
}


/**
  * Page class for the modal for creating a billing project.
  */
class CreateBillingProjectModal(implicit webDriver: WebDriver) extends FireCloudView {

  def createBillingProject(projectName: String, billingAccountName: String): Unit = {
    ui.fillProjectName(projectName)
    ui.selectBillingAccount(billingAccountName)
    ui.clickCreateButton()
  }


  object ui {
    private val createButton: Query = testId("create-project-button")
    private val projectNameInput = testId("project-name-input")

    def clickCreateButton(): Unit = {
      click on createButton
    }

    def fillProjectName(name: String): Unit = {
      await enabled projectNameInput
      textField(projectNameInput).value = name
    }

    def selectBillingAccount(name: String): Unit = {
      click on testId(name)
    }
  }
}
