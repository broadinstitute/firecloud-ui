package org.broadinstitute.dsde.firecloud.test.user

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.component.{Button, Checkbox, Label, TestId}
import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.auth.{AuthToken, TrialBillingAccountAuthToken}
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.fixture.BillingFixtures
import org.broadinstitute.dsde.workbench.fixture.TestReporterFixture
import org.broadinstitute.dsde.workbench.service._
import org.broadinstitute.dsde.workbench.service.test.{CleanUp, WebBrowserSpec}
import org.scalatest.{BeforeAndAfterEach, FreeSpec, Matchers}

import scala.util.Try


/**
  * Tests for new user registration scenarios.
  */
class FreeTrialSpec extends FreeSpec with BeforeAndAfterEach with Matchers with WebBrowserSpec
  with UserFixtures with BillingFixtures with CleanUp with LazyLogging with TestReporterFixture {

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

  private def registerCleanUpForDeleteTrialState(): Unit = {
    register cleanUp  Try(trialKVPKeys foreach { k => Thurloe.keyValuePairs.delete(subjectId, k)})
  }

  private def assertBannerNotVisible():  Unit = {
    withWebDriver { implicit driver =>
      withSignIn(testUser) { _ =>
        await ready new WorkspaceListPage()
        val bannerTitleElement = Label(TestId("trial-banner-title"))
        bannerTitleElement.isVisible shouldBe false
      }
    }
  }

  "A user whose free trial status is" - {

    "Blank" - {
      "should not see the free trial banner" in {
        assertBannerNotVisible()
      }
    }

    List("Enabled", "Disabled", "Finalized") foreach { status =>
      s"$status" - {
        "should not see the free trial banner" in {
          registerCleanUpForDeleteTrialState()
          Thurloe.keyValuePairs.set(subjectId, "trialState", status)
          assertBannerNotVisible()
        }
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

  }
}
