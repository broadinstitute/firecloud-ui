package org.broadinstitute.dsde.firecloud.test.user

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.api.{Orchestration, Thurloe}
import org.broadinstitute.dsde.firecloud.auth.AuthToken
import org.broadinstitute.dsde.firecloud.component.{Button, Label, TestId}
import org.broadinstitute.dsde.firecloud.config.{Config, Credentials, UserPool}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.firecloud.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.util.Try


/**
  * Tests for new user registration scenarios.
  */
class FreeTrialSpec extends FreeSpec with BeforeAndAfter with Matchers with WebBrowserSpec
  with UserFixtures with CleanUp with LazyLogging {

  val adminUser: Credentials = UserPool.chooseAdmin
  implicit val authToken: AuthToken = adminUser.makeAuthToken()

  val testUser: Credentials = Config.Users.testUser  // TODO: pull from user pool and fetch correct subject ID
  val userAuthToken: AuthToken = testUser.makeAuthToken()
  var subjectId : String = Orchestration.profile.getUser()(userAuthToken)("userId").toString // TODO: Use orch endpoint to change trial status


  // Clean-up anything left over from any previous failures.
  before {
    Try(Thurloe.keyValuePairs.delete(subjectId, "trialCurrentState"))
  }

  private def registerCleanUpForDeleteTrialState(subjectId: String): Unit = {
    register cleanUp Thurloe.keyValuePairs.delete(subjectId, "trialCurrentState")
  }

  "FireCloud" - {

    "should not show the free trial banner to a non-enabled user" in withWebDriver { implicit driver =>
      withSignIn(testUser) { _ =>
        await ready new WorkspaceListPage()
        val bannerTitle = Label(TestId("trial-banner-title")).getText
        bannerTitle shouldBe ""
      }
    }

    "should show the free trial banner to an enabled user and allow them to enroll" in withWebDriver { implicit driver =>
      registerCleanUpForDeleteTrialState(subjectId)
      Thurloe.keyValuePairs.set(subjectId, "trialCurrentState", "Enabled")

      withSignIn(testUser) { _ =>
        await ready new WorkspaceListPage()
        val bannerTitleEl = Label(TestId("trial-banner-title"))
        bannerTitleEl.getText shouldBe "Welcome to FireCloud!"

        val bannerButton = Button(TestId("trial-banner-button"))
        bannerButton.doClick()
        await condition bannerButton.getState == "ready"
        bannerTitleEl.getText shouldBe "Access Free Credits"
      }
    }

    "should show a terminated user that they are inactive" in withWebDriver { implicit driver =>
      registerCleanUpForDeleteTrialState(subjectId)
      Thurloe.keyValuePairs.set(subjectId, "trialCurrentState", "Terminated")

      withSignIn(testUser) { _ =>
        await ready new WorkspaceListPage()
        val bannerTitle = Label(TestId("trial-banner-title")).getText
        bannerTitle shouldBe "Your free credits have expired"
      }
    }
  }
}
