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


  // Clean-up anything left over from any previous failures.
  override def beforeEach {
    testUser = UserPool.chooseStudent
    userAuthToken = testUser.makeAuthToken()
    subjectId = Orchestration.profile.getUser()(userAuthToken)("userId").toString
    Try(Thurloe.keyValuePairs.delete(subjectId, "trialState"))
  }

  private def registerCleanUpForDeleteTrialState(subjectId: String): Unit = {
    register cleanUp Thurloe.keyValuePairs.delete(subjectId, "trialState")
  }

  "FireCloud" - {

    "should not show the free trial banner to a non-enabled user" in withWebDriver { implicit driver =>
      withSignIn(testUser) { _ =>
        await ready new WorkspaceListPage()
        val bannerTitleElement = Label(TestId("trial-banner-title"))
        bannerTitleElement.isVisible shouldBe false
      }
    }

    "should show the free trial banner to an enabled user and allow them to enroll" in withWebDriver { implicit driver =>
      registerCleanUpForDeleteTrialState(subjectId)
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

    "should show a terminated user that they are inactive" in withWebDriver { implicit driver =>
      registerCleanUpForDeleteTrialState(subjectId)
      Thurloe.keyValuePairs.set(subjectId, "trialState", "Terminated")

      withSignIn(testUser) { _ =>
        await ready new WorkspaceListPage()
        val bannerTitleElement = Label(TestId("trial-banner-title"))
        bannerTitleElement.isVisible shouldBe true
        bannerTitleElement.getText shouldBe "Your free credits have expired"
      }
    }

    "should not show the free trial banner to a disabled user" in withWebDriver { implicit driver =>
      registerCleanUpForDeleteTrialState(subjectId)
      Thurloe.keyValuePairs.set(subjectId, "trialState", "Disabled")

      withSignIn(testUser) { _ =>
        await ready new WorkspaceListPage()
        val bannerTitleElement = Label(TestId("trial-banner-title"))
        bannerTitleElement.isVisible shouldBe false
      }
    }
  }
}
