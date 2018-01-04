package org.broadinstitute.dsde.firecloud.test.user

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.{Orchestration, Thurloe}
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.component.{Button, Checkbox, Label, TestId}
import org.broadinstitute.dsde.firecloud.config.{Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{BeforeAndAfterEach, FreeSpec, Matchers}

import scala.util.Try


/**
  * Tests for new user registration scenarios.
  */
class FreeTrialSpec extends FreeSpec with BeforeAndAfterEach with Matchers with WebBrowserSpec
  with UserFixtures with CleanUp with LazyLogging {

  var campaignManager: Credentials = _
  var testUser: Credentials = _
  var userAuthToken: AuthToken = _
  var subjectId : String = _
  val trialKVPKeys = Seq("trialState", "trialBillingProjectName")

  override def beforeEach {
    campaignManager = UserPool.chooseCampaignManager
    testUser = UserPool.chooseStudent
    userAuthToken = testUser.makeAuthToken()
    subjectId = Orchestration.profile.getUser()(userAuthToken)("userId").toString
    implicit val token: AuthToken = userAuthToken
    Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  private def setUpEnabledUserAndProject(): Unit = {
    implicit val token: AuthToken = campaignManager.makeAuthToken()
    api.trial.createTrialProjects(1)
    api.trial.enableUser(testUser.email)
  }

  private def registerCleanUpForDeleteTrialState(): Unit = {
    implicit val token: AuthToken = testUser.makeAuthToken()
    register cleanUp  Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  "A user whose free trial status is" - {

    "Blank" - {
      "should not see the free trial banner" in withWebDriver { implicit driver =>
        implicit val token: AuthToken = testUser.makeAuthToken()
        withSignIn(testUser) { _ =>
          await ready new WorkspaceListPage()
          val bannerTitleElement = Label(TestId("trial-banner-title")) // TODO: Define elements in page class
          bannerTitleElement.isVisible shouldBe false
        }
      }
    }

    "Enabled" - {
      "should see the free trial banner and be able to enroll" in withWebDriver { implicit driver =>
        setUpEnabledUserAndProject()
        implicit val token: AuthToken = testUser.makeAuthToken()
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
        implicit val token: AuthToken = testUser.makeAuthToken()
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
        implicit val token: AuthToken = testUser.makeAuthToken()
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
