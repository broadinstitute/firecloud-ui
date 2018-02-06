package org.broadinstitute.dsde.firecloud.test.user

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.component.{Button, Checkbox, Label, TestId}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.auth.{AuthToken, TrialBillingAccountAuthToken}
import org.broadinstitute.dsde.workbench.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.broadinstitute.dsde.workbench.service.{Google, Orchestration, Thurloe}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FreeSpec, Matchers}

import scala.util.Try


/**
  * Tests for new user registration scenarios.
  */
class FreeTrialSpec extends FreeSpec with BeforeAndAfterEach with BeforeAndAfterAll with Matchers with WebBrowserSpec
  with UserFixtures with CleanUp with LazyLogging {

  val adminUser: Credentials = UserPool.chooseAdmin
  val campaignManager: Credentials = UserPool.chooseCampaignManager
  implicit val authToken: AuthToken = adminUser.makeAuthToken()
  val trialKVPKeys = Seq("trialState", "trialBillingProjectName", "trialEnabledDate", "trialEnrolledDate",
    "trialTerminatedDate", "trialExpirationDate", "userAgreed")
  val billingProject: String = Config.Projects.default

  var testUser: Credentials = _
  var userAuthToken: AuthToken = _
  var subjectId : String = _

  /**
    * Set up the number of projects necessary for this entire test suite to run.
    * Currently, that number is 1
    */
  val freeTrialProjectsRequired = 1

  override def beforeAll: Unit = {
    implicit val authToken: AuthToken = campaignManager.makeAuthToken()
    api.trial.createTrialProjects(freeTrialProjectsRequired)
  }

  override def afterAll: Unit = {
    implicit val authToken: AuthToken = campaignManager.makeAuthToken()
    api.trial.reportTrialProjects().foreach { p =>
      logger.info(s"Cleaning up project: ${p.name}")
      register cleanUp api.workspaces.delete(billingProject, p.name)
    }
  }

  override def beforeEach {
    testUser = UserPool.chooseStudent
    userAuthToken = testUser.makeAuthToken()
    subjectId = Orchestration.profile.getUser()(userAuthToken)("userId").toString
    Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  private def enableUser(user: Credentials): Unit = {
    logger.info(s"Attempting to enable user [${user.email}] as campaign manager [${campaignManager.email}]")
    api.trial.enableUser(user.email)
  }

  private def registerCleanUpForDeleteTrialState(): Unit = {
    register cleanUp  Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  "A user whose free trial status is" - {

    "Blank" - {
      "should not see the free trial banner" in withWebDriver { implicit driver =>
        withSignIn(testUser) { _ =>
          await ready new WorkspaceListPage()
          val bannerTitleElement = Label(TestId("trial-banner-title")) // TODO: Define elements in page class
          bannerTitleElement.isVisible shouldBe false
        }
      }
    }

    "Enabled" - {
      "should be able to see the free trial banner, enroll and get terminated" in withWebDriver { implicit driver =>
        enableUser(testUser)
        withSignIn(testUser) { _ =>
          await ready new WorkspaceListPage()
          val bannerTitleElement = Label(TestId("trial-banner-title"))
          bannerTitleElement.isVisible shouldBe true
          bannerTitleElement.getText shouldBe "Welcome to FireCloud!"

          val bannerButton = await ready Button(TestId("trial-banner-button"))
          bannerButton.doClick()

          val reviewButton = await ready Button(TestId("review-terms-of-service"))
          reviewButton.doClick()

          val agreeTermsCheckbox = await ready Checkbox(TestId("agree-terms"))
          agreeTermsCheckbox.ensureChecked()

          val agreeCloudTermsCheckbox = await ready Checkbox(TestId("agree-cloud-terms"))
          agreeCloudTermsCheckbox.ensureChecked()

          val acceptButton = await ready Button(TestId("accept-terms-of-service"))
          acceptButton.doClick()

          await condition bannerButton.getState == "ready"
          bannerTitleElement.getText shouldBe "Access Free Credits"
        }

        // Verify that the user has been added to the corresponding billing project
        val billingProject = Thurloe.keyValuePairs.getAll(subjectId).get("trialBillingProjectName")
        assert(billingProject.nonEmpty, s"No trial billing project was allocated for the user ${testUser.email}.")

        val userBillingProjects = api.profile.getUserBillingProjects()(userAuthToken)
        assert(userBillingProjects.nonEmpty, s"The trial user ${testUser.email} has no billing projects.")

        val userHasTheRightBillingProject: Boolean = userBillingProjects.exists(_.values.toList.contains(billingProject.get))
        assert(userHasTheRightBillingProject)

        // Verify that the user's project is removed from the account upon termination
        val trialAuthToken = TrialBillingAccountAuthToken()
        val billingAccountUponEnrollment = Google.billing.getBillingProjectAccount(billingProject.get)(trialAuthToken)
        assert(billingAccountUponEnrollment.nonEmpty, s"The user's project is not associated with a billing account.")

        api.trial.terminateUser(testUser.email)

        val billingAccountUponTermination = Google.billing.getBillingProjectAccount(billingProject.get)(trialAuthToken)
        val errMsg = "The trial user's billing project should have been removed from the billing account."
        assert(billingAccountUponTermination.isEmpty, errMsg)

        registerCleanUpForDeleteTrialState()
      }
    }

    "Terminated" - {
      "should see that they are inactive" in withWebDriver { implicit driver =>
        registerCleanUpForDeleteTrialState()
        Thurloe.keyValuePairs.set(subjectId, "trialState", "Terminated")

        withSignIn(testUser) { _ =>
          await ready new WorkspaceListPage()
          val bannerTitleElement = Label(TestId("trial-banner-title"))
          bannerTitleElement.isVisible shouldBe true
          bannerTitleElement.getText shouldBe "Your free credits have expired"
        }
      }
    }

    "Disabled" - {
      "should not see the free trial banner" in withWebDriver { implicit driver =>
        registerCleanUpForDeleteTrialState()
        Thurloe.keyValuePairs.set(subjectId, "trialState", "Disabled")

        withSignIn(testUser) { _ =>
          await ready new WorkspaceListPage()
          val bannerTitleElement = Label(TestId("trial-banner-title"))
          bannerTitleElement.isVisible shouldBe false
        }
      }
    }
   
   "Finalized" - {
      "should not see the free trial banner" in withWebDriver { implicit driver =>
        registerCleanUpForDeleteTrialState()
        Thurloe.keyValuePairs.set(subjectId, "trialState", "Finalized")

        withSignIn(testUser) { _ =>
          await ready new WorkspaceListPage()
          val bannerTitleElement = Label(TestId("trial-banner-title"))
          bannerTitleElement.isVisible shouldBe false
        }
      }
    }
  }
}
