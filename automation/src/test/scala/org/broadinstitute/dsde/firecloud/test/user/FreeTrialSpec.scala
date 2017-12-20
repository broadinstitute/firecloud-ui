package org.broadinstitute.dsde.firecloud.test.user

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.{Orchestration, Thurloe}
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.component.{Button, Label, TestId}
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

  val adminUser: Credentials = UserPool.chooseAdmin
  implicit val authToken: AuthToken = adminUser.makeAuthToken()

  var testUser: Credentials = _
  var userAuthToken: AuthToken = _
  var subjectId : String = _


  override def beforeEach {
    testUser = UserPool.chooseStudent
    userAuthToken = testUser.makeAuthToken()
    subjectId = Orchestration.profile.getUser()(userAuthToken)("userId").toString
    Try(Thurloe.keyValuePairs.delete(subjectId, "trialState"))
  }

  private def registerCleanUpForDeleteTrialState(): Unit = {
    register cleanUp Thurloe.keyValuePairs.delete(subjectId, "trialState")
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
      "should see the free trial banner and be able to enroll" ignore withWebDriver { implicit driver => // ignored until trial admin group is ready in fiab
        registerCleanUpForDeleteTrialState()
        Thurloe.keyValuePairs.set(subjectId, "trialState", "Enabled")

        withSignIn(testUser) { _ =>
          await ready new WorkspaceListPage()
          val bannerTitleElement = Label(TestId("trial-banner-title"))
          bannerTitleElement.isVisible shouldBe true
          bannerTitleElement.getText shouldBe "Welcome to FireCloud!"

          val bannerButton = Button(TestId("trial-banner-button"))
          bannerButton.doClick()
          await condition bannerButton.getState == "ready"
          bannerTitleElement.getText shouldBe "Access Free Credits"
        }
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
