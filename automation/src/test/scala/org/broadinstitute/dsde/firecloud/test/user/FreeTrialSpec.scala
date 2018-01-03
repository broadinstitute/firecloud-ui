package org.broadinstitute.dsde.firecloud.test.user

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.{Orchestration, Thurloe}
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.component.{Button, Label, TestId}
import org.broadinstitute.dsde.firecloud.config.{Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.MessageModal
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
  var campaignManagerAuthToken: AuthToken = _
  var testUser: Credentials = _
  var userAuthToken: AuthToken = _
  var subjectId : String = _
  val trialKVPKeys = Seq("trialState", "trialBillingProjectName")

  override def beforeEach {
    campaignManager = UserPool.chooseCampaignManager
    campaignManagerAuthToken = campaignManager.makeAuthToken()
    testUser = UserPool.chooseStudent
    userAuthToken = testUser.makeAuthToken()
    subjectId = Orchestration.profile.getUser()(userAuthToken)("userId").toString
    implicit val token: AuthToken = userAuthToken
    Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  private def registerCleanUpForDeleteTrialState(): Unit = {
    implicit val token: AuthToken = userAuthToken
    register cleanUp  Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  private def createFreeTierProject(): Unit = {
    logger.info("Creating Free Tier Project")
    api.trial.createTrialProjects(1)(token = campaignManagerAuthToken)
  }

  private def enableUser(): Unit = {
    api.trial.enableUser(testUser.email)(token = campaignManagerAuthToken)
  }

  "A user whose free trial status is" - {

    "blank" - {
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
        createFreeTierProject()
        registerCleanUpForDeleteTrialState()
        enableUser()

        implicit val token: AuthToken = userAuthToken
        withSignIn(testUser) { _ =>
          await ready new WorkspaceListPage()
          val bannerTitleElement = Label(TestId("trial-banner-title"))
          bannerTitleElement.isVisible shouldBe true
          bannerTitleElement.getText shouldBe "Welcome to FireCloud!"

          val bannerButton = Button(TestId("trial-banner-button"))
          bannerButton.doClick()
          val msgModal = await ready MessageModal()
          msgModal.clickOk()

          await condition bannerButton.getState == "ready"
          bannerTitleElement.getText shouldBe "Access Free Credits"
        }
      }
    }

    "Terminated" - {
      "should see that they are inactive" in withWebDriver { implicit driver =>
        registerCleanUpForDeleteTrialState()
        implicit val token: AuthToken = userAuthToken
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
        implicit val token: AuthToken = userAuthToken
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
