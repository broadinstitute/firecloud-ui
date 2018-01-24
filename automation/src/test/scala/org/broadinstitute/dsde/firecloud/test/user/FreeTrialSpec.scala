package org.broadinstitute.dsde.firecloud.test.user

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.component.{Button, Checkbox, Label, TestId}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.service.{Orchestration, Thurloe}
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
      "should see the free trial banner and be able to enroll" in withWebDriver { implicit driver =>
        setUpEnabledUserAndProject(testUser)
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
  }
}
