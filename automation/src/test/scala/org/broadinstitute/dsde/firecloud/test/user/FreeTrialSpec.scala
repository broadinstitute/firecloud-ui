package org.broadinstitute.dsde.firecloud.test.user

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.component.{Button, Checkbox, Label, TestId}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.auth.{AuthToken, ServiceAccountAuthTokenFromPem, TrialBillingAccountAuthToken}
import org.broadinstitute.dsde.workbench.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.workbench.model.{UserInfo, WorkbenchEmail, WorkbenchUserId}
import org.broadinstitute.dsde.workbench.service._
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{BeforeAndAfterEach, FreeSpec, Matchers}

import scala.util.Try


/**
  * Tests for new user registration scenarios.
  */
class FreeTrialSpec extends FreeSpec with BeforeAndAfterEach with Matchers with WebBrowserSpec
  with UserFixtures with CleanUp with LazyLogging {

  val adminUser: Credentials = UserPool.chooseAdmin
  val campaignManager: Credentials = UserPool.chooseCampaignManager
  implicit val authToken: AuthToken = adminUser.makeAuthToken()
  val trialKVPKeys = Seq("trialState", "trialBillingProjectName", "trialEnabledDate", "trialEnrolledDate",
    "trialTerminatedDate", "trialExpirationDate", "userAgreed")

  var testUser: Credentials = _
  var userAuthToken: AuthToken = _
  var subjectId : String = _

  override def beforeEach {
    testUser = UserPool.chooseStudent
    userAuthToken = testUser.makeAuthToken()
    subjectId = Orchestration.profile.getUser()(userAuthToken)("userId").toString
    Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  private def setUpEnabledUserAndProject(user: Credentials): Unit = {
    implicit val authToken: AuthToken = campaignManager.makeAuthToken()
    api.trial.createTrialProjects(1)
    logger.info(s"Attempting to enable user [${user.email}] as campaign manager [${campaignManager.email}]")
    api.trial.enableUser(user.email)
  }

  private def registerCleanUpForDeleteTrialState(): Unit = {
    register cleanUp  Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  "A user whose free trial status is" - {

    "Blank" - {
      "should not see the free trial banner" in {
        withWebDriver { implicit driver =>
          withSignIn(testUser) { _ =>
            await ready new WorkspaceListPage()
            val bannerTitleElement = Label(TestId("trial-banner-title")) // TODO: Define elements in page class
            bannerTitleElement.isVisible shouldBe false
          }
        }
      }
    }

    "Enabled" - {
      "should be able to see the free trial banner, enroll and get terminated" in {
        setUpEnabledUserAndProject(testUser)
        val trialAuthToken = TrialBillingAccountAuthToken()
        api.trial.reportTrialProjects().foreach { x =>
          register cleanUp Try(Rawls.admin.deleteBillingProject(x.name, UserInfo(OAuth2BearerToken(trialAuthToken.value),
            WorkbenchUserId("0"), WorkbenchEmail("doesnt@matter.com"), 3600))(UserPool.chooseAdmin.makeAuthToken())).recover {
              case ex: RestException => logger.warn(s"RestException occurred in Rawls.admin.deleteBillingProject(${x.name})", ex)
          }
        }
        withWebDriver { implicit driver =>
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
        }

        // Verify that the user has been added to the corresponding billing project
        val billingProject = Thurloe.keyValuePairs.getAll(subjectId).get("trialBillingProjectName")
        assert(billingProject.nonEmpty, s"No trial billing project was allocated for the user ${testUser.email}.")

        val userBillingProjects = api.profile.getUserBillingProjects()(userAuthToken)
        assert(userBillingProjects.nonEmpty, s"The trial user ${testUser.email} has no billing projects.")

        val userHasTheRightBillingProject: Boolean = userBillingProjects.exists(_.values.toList.contains(billingProject.get))
        assert(userHasTheRightBillingProject)

        // Verify that the user's project is removed from the account upon termination
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
      "should see that they are inactive" in {
        registerCleanUpForDeleteTrialState()
        Thurloe.keyValuePairs.set(subjectId, "trialState", "Terminated")
        withWebDriver { implicit driver =>
          withSignIn(testUser) { _ =>
            await ready new WorkspaceListPage()
            val bannerTitleElement = Label(TestId("trial-banner-title"))
            bannerTitleElement.isVisible shouldBe true
            bannerTitleElement.getText shouldBe "Your free credits have expired"
          }
        }
      }
    }

    "Disabled" - {
      "should not see the free trial banner" in {
        registerCleanUpForDeleteTrialState()
        Thurloe.keyValuePairs.set(subjectId, "trialState", "Disabled")
        withWebDriver { implicit driver =>
          withSignIn(testUser) { _ =>
            await ready new WorkspaceListPage()
            val bannerTitleElement = Label(TestId("trial-banner-title"))
            bannerTitleElement.isVisible shouldBe false
          }
        }
      }
    }
   
   "Finalized" - {
      "should not see the free trial banner" in {
        registerCleanUpForDeleteTrialState()
        Thurloe.keyValuePairs.set(subjectId, "trialState", "Finalized")
        withWebDriver { implicit driver =>
          withSignIn(testUser) { _ =>
            await ready new WorkspaceListPage()
            val bannerTitleElement = Label(TestId("trial-banner-title"))
            bannerTitleElement.isVisible shouldBe false
          }
        }
      }
    }
  }

}
