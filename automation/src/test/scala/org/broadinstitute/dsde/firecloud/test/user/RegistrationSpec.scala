package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.fixture.{FailTestRetryable, UserFixtures}
import org.broadinstitute.dsde.firecloud.page.library.DataLibraryPage
import org.broadinstitute.dsde.firecloud.page.user.ProfilePage
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspaceListPage
import org.broadinstitute.dsde.workbench.auth.AuthToken
import org.broadinstitute.dsde.workbench.config.{Credentials, UserPool}
import org.broadinstitute.dsde.workbench.service.{Sam, Thurloe}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.scalatest.tagobjects.Retryable
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}


/**
  * Tests for new user registration scenarios.
  */
class RegistrationSpec extends FreeSpec with FailTestRetryable with BeforeAndAfter with Matchers with WebBrowserSpec with UserFixtures {

  val testUser: Credentials = FireCloudConfig.Users.temp // TODO: pull from user pool and fetch correct subject ID
  val subjectId: String = FireCloudConfig.Users.tempSubjectId

  val adminUser: Credentials = UserPool.chooseAdmin
  implicit val authToken: AuthToken = adminUser.makeAuthToken()

  // Clean-up anything left over from any previous failures.
  before {
    if (Sam.admin.doesUserExist(subjectId).getOrElse(false)) {
      try {
        Sam.admin.deleteUser(subjectId)
      } catch nonFatalAndLog("Error deleting user before test but will try running the test anyway")
    }
    Thurloe.keyValuePairs.deleteAll(subjectId)
  }

  private def registerCleanUpForDeleteUser(subjectId: String): Unit = {
    register cleanUp Sam.admin.deleteUser(subjectId)
    register cleanUp Thurloe.keyValuePairs.deleteAll(subjectId)
  }

//  "FireCloud registration" - {
//
//    "should allow a person to register" taggedAs Retryable in {
//      withWebDriver { implicit driver =>
//        withSignInNewUserReal(testUser) { registrationPage =>
//          registerCleanUpForDeleteUser(subjectId)
//
//          registrationPage.register(
//            firstName = "Test",
//            lastName = "Dummy",
//            title = "Tester",
//            contactEmail = Some("test@firecloud.org"),
//            institute = "Broad",
//            institutionalProgram = "DSDE",
//            nonProfitStatus = true,
//            principalInvestigator = "Nobody",
//            city = "Cambridge",
//            state = "MA",
//            country = "USA")
//
//          new DataLibraryPage().validateLocation()
//
//          val profilePage = new ProfilePage().open
//          val username = testUser.email.split("@").head
//          /* Re-enable this code and remove the temporary code below after fixing rawls for GAWB-2933
//          profilePage.readProxyGroupEmail should (startWith (username) and endWith ("firecloud.org"))
//          */
//          profilePage.readProxyGroupEmail should endWith("firecloud.org")
//        }
//      }
//    }
//
//    "should show billing account instructions for a newly registered user" taggedAs Retryable in {
//
//      withWebDriver { implicit driver =>
//        withSignInNewUserReal(testUser) { registrationPage =>
//          registerCleanUpForDeleteUser(subjectId)
//
//          registrationPage.register(
//            firstName = "Test",
//            lastName = "Dummy",
//            title = "Tester",
//            contactEmail = Some("test@firecloud.org"),
//            institute = "Broad",
//            institutionalProgram = "DSDE",
//            nonProfitStatus = true,
//            principalInvestigator = "Nobody",
//            city = "Cambridge",
//            state = "MA",
//            country = "USA")
//
//          await ready new DataLibraryPage()
//
//          val listPage = new WorkspaceListPage().open
//          listPage.clickCreateWorkspaceButton(true)
//
//          listPage.showsNoBillingProjectsModal() shouldBe true
//
//          listPage.closeModal()
//        }
//      }
//    }
//
//    /*
//      behavior of "FireCloud registration page"
//
//      it should "allow sign in of registered user" in withWebDriver { implicit driver =>
//        new SignInPage(FireCloudConfig.FireCloud.baseUrl).open.signIn(FireCloudConfig.Users.testUser.email, FireCloudConfig.Users.testUser.password)
//      }
//
//      it should "not allow an unregistered user access" in withWebDriver { implicit driver =>
//      }
//
//      it should "allow a signed-in user to log out" in withWebDriver { implicit driver =>
//      }
//    */
//  }

}
